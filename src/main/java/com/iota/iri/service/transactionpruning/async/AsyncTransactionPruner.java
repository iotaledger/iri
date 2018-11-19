package com.iota.iri.service.transactionpruning.async;

import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.transactionpruning.TransactionPruner;
import com.iota.iri.service.transactionpruning.TransactionPrunerJob;
import com.iota.iri.service.transactionpruning.TransactionPruningException;
import com.iota.iri.service.transactionpruning.jobs.MilestonePrunerJob;
import com.iota.iri.service.transactionpruning.jobs.UnconfirmedSubtanglePrunerJob;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.thread.ThreadIdentifier;
import com.iota.iri.utils.thread.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates a {@link TransactionPruner} that is able to process it's jobs asynchronously in the background and persists
 * its state in a file on the hard disk of the node.<br />
 * <br />
 * The asynchronous processing of the jobs is done through {@link Thread}s that are started and stopped by invoking the
 * corresponding {@link #start()} and {@link #shutdown()} methods. Since some of the builtin jobs require a special
 * logic for the way they are executed, we register the builtin job types here.<br />
 */
public class AsyncTransactionPruner implements TransactionPruner {
    /**
     * The interval in milliseconds that the {@link AsyncTransactionPruner} will check if new cleanup tasks are
     * available and need to be processed.
     */
    private static final int GARBAGE_COLLECTOR_RESCAN_INTERVAL = 10000;

    /**
     * The interval (in milliseconds) in which the {@link AsyncTransactionPruner} will persist its state.
     *
     * Note: Since the worst thing that could happen when not having a 100% synced state file is to have a few floating
     *       "zombie" transactions in the database, we do not persist the state immediately but in intervals in a
     *       separate {@link Thread} (to save performance - until a db-based version gets introduced).
     */
    private static final int GARBAGE_COLLECTOR_PERSIST_INTERVAL = 1000;

    /**
     * Logger for this class allowing us to dump debug and status messages.
     */
    private static final Logger log = LoggerFactory.getLogger(AsyncTransactionPruner.class);

    /**
     * Tangle object which acts as a database interface.
     */
    private Tangle tangle;

    /**
     * Data provider for the snapshots that are relevant for the node.
     */
    private SnapshotProvider snapshotProvider;

    /**
     * Manager for the tips (required for removing pruned transactions from this manager).
     */
    private TipsViewModel tipsViewModel;

    /**
     * Configuration with important snapshot related parameters.
     */
    private SnapshotConfig config;

    /**
     * Holds a reference to the {@link ThreadIdentifier} for the cleanup thread.
     *
     * Using a {@link ThreadIdentifier} for spawning the thread allows the {@link ThreadUtils} to spawn exactly one
     * thread for this instance even when we call the {@link #start()} method multiple times.
     */
    private final ThreadIdentifier cleanupThreadIdentifier = new ThreadIdentifier("Transaction Pruner");

    /**
     * Holds a reference to the {@link ThreadIdentifier} for the state persistence thread.
     *
     * Using a {@link ThreadIdentifier} for spawning the thread allows the {@link ThreadUtils} to spawn exactly one
     * thread for this instance even when we call the {@link #start()} method multiple times.
     */
    private final ThreadIdentifier persisterThreadIdentifier = new ThreadIdentifier("Transaction Pruner Persister");

    /**
     * A map of {@link JobParser}s allowing us to determine how to parse the jobs from the state file, based on their
     * type.
     */
    private final Map<String, JobParser> jobParsers = new HashMap<>();

    /**
     * List of cleanup jobs that shall get processed by the {@link AsyncTransactionPruner} (grouped by their class).
     */
    private final Map<Class<? extends TransactionPrunerJob>, JobQueue> jobQueues = new HashMap<>();

    /**
     * Holds a flag that indicates if the state shall be persisted.
     */
    private boolean persistRequested = false;

    /**
     * This method initializes the instance and registers its dependencies.<br />
     * <br />
     * It simply stores the passed in values in their corresponding private properties.<br />
     * <br />
     * Note: Instead of handing over the dependencies in the constructor, we register them lazy. This allows us to have
     *       circular dependencies because the instantiation is separated from the dependency injection. To reduce the
     *       amount of code that is necessary to correctly instantiate this class, we return the instance itself which
     *       allows us to still instantiate, initialize and assign in one line - see Example:<br />
     *       <br />
     *       {@code asyncTransactionPruner = new AsyncTransactionPruner().init(...);}
     *
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider data provider for the snapshots that are relevant for the node
     * @param tipsViewModel manager for the tips (required for removing pruned transactions from this manager)
     * @param config Configuration with important snapshot related configuration parameters
     * @return the initialized instance itself to allow chaining
     */
    public AsyncTransactionPruner init(Tangle tangle, SnapshotProvider snapshotProvider, TipsViewModel tipsViewModel,
            SnapshotConfig config) {

        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.tipsViewModel = tipsViewModel;
        this.config = config;

        addJobQueue(UnconfirmedSubtanglePrunerJob.class, new SimpleJobQueue(this));
        addJobQueue(MilestonePrunerJob.class, new MilestonePrunerJobQueue(this, config));

        registerParser(MilestonePrunerJob.class, MilestonePrunerJob::parse);
        registerParser(UnconfirmedSubtanglePrunerJob.class, UnconfirmedSubtanglePrunerJob::parse);

        return this;
    }

    /**
     * {@inheritDoc}
     *
     * It adds the job to its corresponding queue.
     */
    @Override
    public void addJob(TransactionPrunerJob job) throws TransactionPruningException {
        job.setTransactionPruner(this);
        job.setTangle(tangle);
        job.setTipsViewModel(tipsViewModel);
        job.setSnapshot(snapshotProvider.getInitialSnapshot());

        // this call is "unchecked" to a "raw" JobQueue and it is intended since the matching JobQueue is defined by the
        // registered job types
        getJobQueue(job.getClass()).addJob(job);

        saveState();
    }

    /**
     * {@inheritDoc}
     *
     * It iterates through all available queues and triggers the processing of their jobs.
     *
     * @throws TransactionPruningException if anything goes wrong while processing the cleanup jobs
     */
    @Override
    public void processJobs() throws TransactionPruningException {
        for(JobQueue jobQueue : jobQueues.values()) {
            if(Thread.currentThread().isInterrupted()) {
                return;
            }

            jobQueue.processJobs();
        }
    }

    /**
     * {@inheritDoc}
     *
     * We incorporate a background job that periodically saves the state rather than doing it "live", to reduce the cost
     * of this operation. While this can theoretically lead to a situation where the saved state is not 100% correct and
     * the latest changes get lost (if IRI crashes or gets restarted before the new changes could be persisted), the
     * impact is marginal because it only leads to some floating "zombie" transactions that will stay in the database.
     * This will be "solved" once we persist the changes in the database instead of a file on the hard disk. For now the
     * trade off between faster processing times and leaving some garbage is reasonable.
     */
    @Override
    public void saveState() {
        persistRequested = true;
    }

    /**
     * {@inheritDoc}
     *
     * It reads the state by parsing the state file and passing it into the registered parsers for each job type.
     *
     * Every line holds a job entry that starts with the fully qualified class name of the job followed by a ";" and the
     * serialized representation of the job.
     */
    @Override
    public void restoreState() throws TransactionPruningException {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new BufferedInputStream(new FileInputStream(getStateFile())))
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(";", 2);
                if (parts.length >= 2) {
                    JobParser jobParser = jobParsers.get(parts[0]);
                    if (jobParser == null) {
                        throw new TransactionPruningException("could not determine a parser for cleanup job of type " + parts[0]);
                    }

                    addJob(jobParser.parse(parts[1]));
                }
            }
        } catch (IOException e) {
            if (getStateFile().exists()) {
                throw new TransactionPruningException("could not read the state file", e);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * It cycles through all registered {@link JobQueue}s and clears them before persisting the state.
     */
    @Override
    public void clear() throws TransactionPruningException {
        for (JobQueue jobQueue : jobQueues.values()) {
            jobQueue.clear();
        }

        saveStateNow();
    }

    /**
     * This method starts the cleanup and persistence {@link Thread}s that asynchronously process the queued jobs in the
     * background.
     *
     * Note: This method is thread safe since we use a {@link ThreadIdentifier} to address the {@link Thread}. The
     *       {@link ThreadUtils} take care of only launching exactly one {@link Thread} that is not terminated.
     */
    public void start() {
        ThreadUtils.spawnThread(this::processJobsThread, cleanupThreadIdentifier);
        ThreadUtils.spawnThread(this::persistThread, persisterThreadIdentifier);
    }

    /**
     * Shuts down the background job by setting the corresponding shutdown flag.
     */
    public void shutdown() {
        ThreadUtils.stopThread(cleanupThreadIdentifier);
        ThreadUtils.stopThread(persisterThreadIdentifier);
    }

    /**
     * This method contains the logic for the processing of the cleanup jobs, that gets executed in a separate
     * {@link Thread}.
     *
     * It repeatedly calls {@link #processJobs()} until the TransactionPruner is shutting down.
     */
    private void processJobsThread() {
        while(!Thread.currentThread().isInterrupted()) {
            try {
                processJobs();
            } catch(TransactionPruningException e) {
                log.error("error while processing the transaction pruner jobs", e);
            }

            ThreadUtils.sleep(GARBAGE_COLLECTOR_RESCAN_INTERVAL);
        }
    }

    /**
     * This method contains the logic for persisting the pruner state, that gets executed in a separate {@link Thread}.
     *
     * It periodically checks the {@link #persistRequested} flag and triggers the writing of the state file until the
     * {@link AsyncTransactionPruner} is shutting down.
     */
    private void persistThread() {
        while(!Thread.currentThread().isInterrupted()) {
            try {
                if (persistRequested) {
                    saveStateNow();

                    persistRequested = false;
                }
            } catch(TransactionPruningException e) {
                log.error("could not persist transaction pruner state", e);
            }

            ThreadUtils.sleep(GARBAGE_COLLECTOR_PERSIST_INTERVAL);
        }
    }

    /**
     * This method creates a serialized version of the given job.
     *
     * @param job job that shall get serialized
     * @return serialized representation of the job
     */
    private String serializeJobEntry(TransactionPrunerJob job) {
        return job.getClass().getCanonicalName() + ";" + job.serialize();
    }

    /**
     * Saves the state by serializing the jobs into a state file that is stored on the hard disk of the node.
     *
     * If no jobs are queued it removes the state file.
     *
     * @throws TransactionPruningException if anything goes wrong while persisting the state
     */
    private void saveStateNow() throws TransactionPruningException {
        try {
            AtomicInteger jobsPersisted = new AtomicInteger(0);

            Files.write(
                Paths.get(getStateFile().getAbsolutePath()),
                () -> jobQueues.values().stream()
                      .<TransactionPrunerJob>flatMap(JobQueue::stream)
                      .<CharSequence>map(jobEntry -> {
                          jobsPersisted.incrementAndGet();

                          return this.serializeJobEntry(jobEntry);
                      })
                      .iterator()
            );

            if (jobsPersisted.get() == 0) {
                try {
                    Files.deleteIfExists(Paths.get(getStateFile().getAbsolutePath()));
                } catch (IOException e) {
                    throw new TransactionPruningException("failed to remove the state file", e);
                }
            }
        } catch(Exception e) {
            throw new TransactionPruningException("failed to write the state file", e);
        }
    }

    /**
     * Registers the job queue that is responsible for processing the jobs of the given type.
     *
     * The {@link JobQueue} implements the specific logic how the jobs are being executed and scheduled.
     *
     * @param jobClass type of the job that we want to be able to process
     * @param jobQueue the queue that is responsible for processing the transactions
     */
    private void addJobQueue(Class<? extends TransactionPrunerJob> jobClass, JobQueue jobQueue) {
        jobQueues.put(jobClass, jobQueue);
    }

    /**
     * This method allows to register a {@link JobParser} for a given job type.
     *
     * When we serialize the pending jobs to save the current state, we also dump their class names, which allows us to
     * generically parse their serialized representation using the registered parser function back into the
     * corresponding job.
     *
     * @param jobClass class of the job that the TransactionPruner shall be able to handle
     * @param jobParser parser function for the serialized version of jobs of the given type
     */
    private void registerParser(Class<?> jobClass, JobParser jobParser) {
        this.jobParsers.put(jobClass.getCanonicalName(), jobParser);
    }

    /**
     * This method retrieves the job queue belonging to a given job type.
     *
     * Before returning the {@link JobQueue} we check if one exists.
     *
     * @param jobClass type of the job that we want to retrieve the queue for
     * @return the list of jobs for the provided job type
     * @throws TransactionPruningException if there is no {@link JobQueue} for the given job type
     */
    private JobQueue getJobQueue(Class<? extends TransactionPrunerJob> jobClass) throws TransactionPruningException {
        JobQueue jobQueue = jobQueues.get(jobClass);

        if(jobQueue == null) {
            throw new TransactionPruningException("jobs of type \"" + jobClass.getCanonicalName() + "\" are not supported");
        }

        return jobQueue;
    }

    /**
     * This method returns a file handle to state file.
     *
     * It constructs the path of the file by appending the corresponding file extension to the
     * {@link com.iota.iri.conf.BaseIotaConfig#localSnapshotsBasePath} config variable. If the path is relative, it
     * places the file relative to the current working directory, which is usually the location of the iri.jar.
     *
     * @return File handle to the state file.
     */
    private File getStateFile() {
        return new File(config.getLocalSnapshotsBasePath() + ".snapshot.gc");
    }

    /**
     * Functional interface for the lambda function that takes care of parsing a specific job from its serialized String
     * representation into the corresponding object in memory.
     *
     * @see AsyncTransactionPruner#registerParser(Class, JobParser) to register the parser
     */
    @FunctionalInterface
    private interface JobParser {
        /**
         * Parses the serialized version of a job back into its unserialized object.
         *
         * @param input serialized job
         * @return unserialized job
         * @throws TransactionPruningException if anything goes wrong while parsing the serialized job
         */
        TransactionPrunerJob parse(String input) throws TransactionPruningException;
    }
}
