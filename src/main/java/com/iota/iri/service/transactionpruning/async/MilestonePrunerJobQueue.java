package com.iota.iri.service.transactionpruning.async;

import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.service.transactionpruning.TransactionPruner;
import com.iota.iri.service.transactionpruning.TransactionPrunerJobStatus;
import com.iota.iri.service.transactionpruning.TransactionPruningException;
import com.iota.iri.service.transactionpruning.jobs.MilestonePrunerJob;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Stream;

/**
 * Represents a queue of {@link MilestonePrunerJob}s thar are being executed by the {@link AsyncTransactionPruner}.
 *
 * The {@link AsyncTransactionPruner} uses a separate queue for every job type, to be able to adjust the processing
 * logic based on the type of the job.
 */
public class MilestonePrunerJobQueue implements JobQueue<MilestonePrunerJob> {
    /**
     * Holds the youngest (highest) milestone index that was successfully cleaned (gets updated when a job finishes).
     */
    private int youngestFullyCleanedMilestoneIndex;

    /**
     * Holds a reference to the container of this queue.
     */
    private final TransactionPruner transactionPruner;

    /**
     * Used to internally store the queued jobs.
     */
    private final Deque<MilestonePrunerJob> jobs = new ConcurrentLinkedDeque<>();

    /**
     * Creates a new queue that is tailored to handle {@link MilestonePrunerJob}s.
     *
     * Since the {@link MilestonePrunerJob}s can take a long time until they are fully processed, we handle them in a
     * different way than other jobs and consolidate the queue whenever we add a new job.
     *
     * @param transactionPruner reference to the container of this queue
     * @param snapshotConfig reference to the config with snapshot related parameters
     */
    public MilestonePrunerJobQueue(TransactionPruner transactionPruner, SnapshotConfig snapshotConfig) {
        this.transactionPruner = transactionPruner;

        youngestFullyCleanedMilestoneIndex = snapshotConfig.getMilestoneStartIndex();
    }

    /**
     * {@inheritDoc}
     *
     * Since the {@link MilestonePrunerJob}s can take a long time to finish, we try to consolidate the queue when we add
     * new jobs, so the state file doesn't get to big while the node is busy cleaning up the milestones. To do so, we
     * first check if the job that shall be added isn't covered by existing cleanup jobs, yet.
     *
     * If it targets a milestone outside of the already covered range, we first check if the last existing job can be
     * extended to cover the target milestone index of our new job. If the job can not be appended to an existing job,
     * we add it to the end of our queue.
     *
     * @param job the {@link MilestonePrunerJob} that shall be added to the queue
     * @throws TransactionPruningException if the given job is no {@link MilestonePrunerJob}
     */
    @Override
    public void addJob(MilestonePrunerJob job) {
        synchronized (jobs) {
            MilestonePrunerJob lastMilestonePrunerJob = jobs.peekLast();

            if (adjustedRangeCoversNewMilestone(job, lastMilestonePrunerJob)) {
                if (lastMilestonePrunerJob != null) {
                    // synchronize the status check so we can be 100% sure, that the job will see our modification
                    // BEFORE it finishes and leaves the processing loop
                    synchronized (lastMilestonePrunerJob) {
                        if (lastMilestonePrunerJob.getStatus() != TransactionPrunerJobStatus.DONE) {
                            lastMilestonePrunerJob.setTargetIndex(job.getTargetIndex());

                            return;
                        }
                    }
                }

                jobs.add(job);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        synchronized (jobs) {
            jobs.clear();
        }
    }

    /**
     * {@inheritDoc}
     *
     * It retrieves the first job while leaving it in the queue, so the job can persist its changes when it finishes a
     * sub-task. After the job was successfully processed, we update the internal
     * {@link #youngestFullyCleanedMilestoneIndex} marker (so jobs that might be added later can continue where this job
     * stopped, without having to reexamine all the previous milestones) and remove it from the queue.
     *
     * After every processed job, we persist the changes by calling the {@link TransactionPruner#saveState()} method.
     *
     * @throws TransactionPruningException if anything goes wrong while processing the jobs
     */
    @Override
    public void processJobs() throws TransactionPruningException {
        MilestonePrunerJob currentJob;
        while (!Thread.currentThread().isInterrupted() && (currentJob = jobs.peek()) != null) {
            try {
                currentJob.process();

                youngestFullyCleanedMilestoneIndex = currentJob.getTargetIndex();

                // we always leave the last job in the queue to be able to "serialize" the queue status and allow
                // to skip already processed milestones even when IRI restarts
                if (jobs.size() == 1) {
                    break;
                }

                jobs.poll();
            } finally {
                transactionPruner.saveState();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Stream<MilestonePrunerJob> stream() {
        return jobs.stream();
    }

    /**
     * This method checks the range of a new cleanup job and adjusts it to reflect the progress made by previous jobs.
     *
     * We first check if the cleanup target was covered by previous jobs already. If the target index addresses a new
     * milestone we adjust the starting and current index to account for the progress made by previous jobs.
     *
     * @param job the new job that shall be checked and adjusted to the previous progress
     * @param lastMilestonePrunerJob the last cleanup job in the queue
     * @return true if the job still has a range that has to be cleaned up or false otherwise
     */
    private boolean adjustedRangeCoversNewMilestone(MilestonePrunerJob job, MilestonePrunerJob lastMilestonePrunerJob) {
        int lastTargetIndex = lastMilestonePrunerJob != null
                ? lastMilestonePrunerJob.getTargetIndex()
                : youngestFullyCleanedMilestoneIndex;

        if (job.getTargetIndex() <= lastTargetIndex) {
            return false;
        }

        if (lastTargetIndex >= job.getStartingIndex()) {
            job.setStartingIndex(lastTargetIndex + 1);
        }
        if (lastTargetIndex >= job.getCurrentIndex()) {
            job.setCurrentIndex(lastTargetIndex + 1);
        }

        return true;
    }
}
