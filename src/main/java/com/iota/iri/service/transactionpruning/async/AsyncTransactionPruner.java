package com.iota.iri.service.transactionpruning.async;

import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.spentaddresses.SpentAddressesProvider;
import com.iota.iri.service.spentaddresses.SpentAddressesService;
import com.iota.iri.service.transactionpruning.TransactionPruner;
import com.iota.iri.service.transactionpruning.TransactionPrunerJob;
import com.iota.iri.service.transactionpruning.TransactionPruningException;
import com.iota.iri.service.transactionpruning.jobs.MilestonePrunerJob;
import com.iota.iri.service.transactionpruning.jobs.UnconfirmedSubtanglePrunerJob;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.thread.ThreadIdentifier;
import com.iota.iri.utils.thread.ThreadUtils;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Creates a {@link TransactionPruner} that is able to process it's jobs asynchronously in the background and persists
 * its state in a file on the hard disk of the node.
 * </p>
 * <p>
 * The asynchronous processing of the jobs is done through {@link Thread}s that are started and stopped by invoking the
 * corresponding {@link #start()} and {@link #shutdown()} methods. Since some of the builtin jobs require a special
 * logic for the way they are executed, we register the builtin job types here.
 * </p>
 */
public class AsyncTransactionPruner implements TransactionPruner {
    /**
     * The interval in milliseconds that the {@link AsyncTransactionPruner} will check if new cleanup tasks are
     * available and need to be processed.
     */
    private static final int GARBAGE_COLLECTOR_RESCAN_INTERVAL = 10000;

    /**
     * <p>
     * The interval (in milliseconds) in which the {@link AsyncTransactionPruner} will persist its state.
     * </p>
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
    private final Tangle tangle;

    /**
     * Data provider for the snapshots that are relevant for the node.
     */
    private final SnapshotProvider snapshotProvider;

    /**
     * Used to check whether an address was spent from before it is pruned.
     */
    private final SpentAddressesService spentAddressesService;

    /**
     * Used to check whether an address is already persisted in the persistence layer.
     */
    private final SpentAddressesProvider spentAddressesProvider;

    /**
     * Manager for the tips (required for removing pruned transactions from this manager).
     */
    private final TipsViewModel tipsViewModel;

    /**
     * Configuration with important snapshot related parameters.
     */
    private final SnapshotConfig config;

    /**
     * Holds a reference to the {@link ThreadIdentifier} for the cleanup thread.
     * <p>
     * Using a {@link ThreadIdentifier} for spawning the thread allows the {@link ThreadUtils} to spawn exactly one
     * thread for this instance even when we call the {@link #start()} method multiple times.
     * </p>
     */
    private final ThreadIdentifier cleanupThreadIdentifier = new ThreadIdentifier("Transaction Pruner");

    /**
     * Holds a reference to the {@link ThreadIdentifier} for the state persistence thread.
     * <p>
     * Using a {@link ThreadIdentifier} for spawning the thread allows the {@link ThreadUtils} to spawn exactly one
     * thread for this instance even when we call the {@link #start()} method multiple times.
     * </p>
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
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider data provider for the snapshots that are relevant for the node
     * @param tipsViewModel manager for the tips (required for removing pruned transactions from this manager)
     * @param config Configuration with important snapshot related configuration parameters
     */
    public AsyncTransactionPruner(Tangle tangle, SnapshotProvider snapshotProvider,
                                       SpentAddressesService spentAddressesService,
                                       SpentAddressesProvider spentAddressesProvider,
                                       TipsViewModel tipsViewModel,
                                       SnapshotConfig config) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.spentAddressesService = spentAddressesService;
        this.spentAddressesProvider = spentAddressesProvider;
        this.tipsViewModel = tipsViewModel;
        this.config = config;
    }

    @Override
    public void init() {
        addJobQueue(UnconfirmedSubtanglePrunerJob.class, new SimpleJobQueue(this));
        addJobQueue(MilestonePrunerJob.class, new MilestonePrunerJobQueue(this, config));
        registerParser(MilestonePrunerJob.class, MilestonePrunerJob::parse);
        registerParser(UnconfirmedSubtanglePrunerJob.class, UnconfirmedSubtanglePrunerJob::parse);
    }

    /**
     * {@inheritDoc}
     *
     * It adds the job to its corresponding queue.
     */
    @Override
    public void addJob(TransactionPrunerJob job) throws TransactionPruningException {
        job.setTransactionPruner(this);
        job.setSpentAddressesService(spentAddressesService);
        job.setSpentAddressesProvider(spentAddressesProvider);
        job.setTangle(tangle);
        job.setTipsViewModel(tipsViewModel);
        job.setSnapshot(snapshotProvider.getInitialSnapshot());

        // this call is "unchecked" to a "raw" JobQueue and it is intended since the matching JobQueue is defined by the
        // registered job types
        getJobQueue(job.getClass()).addJob(job);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * It iterates through all available queues and triggers the processing of their jobs.
     * </p>
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
     * <p>
     * This method removes all queued jobs and resets the state of the {@link TransactionPruner}. It can for example be
     * used to cleanup after tests.
     * </p>
     * It cycles through all registered {@link JobQueue}s and clears them before persisting the state.
     */
    void clear() {
        for (JobQueue jobQueue : jobQueues.values()) {
            jobQueue.clear();
        }
    }

    /**
     * <p>
     * This method starts the cleanup and persistence {@link Thread}s that asynchronously process the queued jobs in the
     * background.
     * </p>
     * <p>
     * Note: This method is thread safe since we use a {@link ThreadIdentifier} to address the {@link Thread}. The
     *       {@link ThreadUtils} take care of only launching exactly one {@link Thread} that is not terminated.
     * </p>
     */
    public void start() {
        ThreadUtils.spawnThread(this::processJobsThread, cleanupThreadIdentifier);
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
     * <p>
     * When we serialize the pending jobs to save the current state, we also dump their class names, which allows us to
     * generically parse their serialized representation using the registered parser function back into the
     * corresponding job.
     * </p>
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
