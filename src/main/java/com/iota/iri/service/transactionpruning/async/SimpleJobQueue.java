package com.iota.iri.service.transactionpruning.async;

import com.iota.iri.service.transactionpruning.TransactionPruner;
import com.iota.iri.service.transactionpruning.TransactionPrunerJob;
import com.iota.iri.service.transactionpruning.TransactionPruningException;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Stream;

/**
 * Represents a queue of {@link TransactionPrunerJob}s thar are being executed by the {@link AsyncTransactionPruner}.
 *
 * The {@link AsyncTransactionPruner} uses a separate queue for every job type, to be able to adjust the processing
 * logic based on the type of the job.
 */
public class SimpleJobQueue implements JobQueue<TransactionPrunerJob> {
    /**
     * Holds a reference to the container of this queue.
     */
    private final TransactionPruner transactionPruner;

    /**
     * Used to internally store the queued jobs.
     */
    private final Deque<TransactionPrunerJob> jobs = new ConcurrentLinkedDeque<>();

    /**
     * Creates a queue of jobs that will process them in their insertion order and persist the state after every
     * processed job.
     *
     * This is used by all job types that do not require special routines for getting processed.
     *
     * @param transactionPruner reference to the container of this queue
     */
    public SimpleJobQueue(TransactionPruner transactionPruner) {
        this.transactionPruner = transactionPruner;
    }

    /**
     * {@inheritDoc}
     *
     * It simply adds the job to the underlying {@link #jobs}.
     *
     * Note: Since we always add to the end and remove from the start, we do not need to synchronize this.
     */
    @Override
    public void addJob(TransactionPrunerJob job) {
        jobs.addLast(job);
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
     * It retrieves the first job while leaving it in the queue, so the job can still persist its changes if it is a
     * long running one that has to save its progress while executing sub-tasks. Only after the job was successfully
     * processed, we remove it from the queue and advance to the next one.
     *
     * If an error occurs while executing the job, we add it back to the end of the queue so we can try to execute it
     * another time.
     *
     * After every processed job, we persist the changes by calling the {@link TransactionPruner#saveState()} method.
     */
    @Override
    public void processJobs() throws TransactionPruningException {
        TransactionPrunerJob currentJob;
        while (!Thread.currentThread().isInterrupted() && (currentJob = jobs.peek()) != null) {
            try {
                currentJob.process();

                jobs.poll();
            } catch (TransactionPruningException e) {
                // only add the job back to the end of the queue if the queue wasn't cleaned in the mean time
                synchronized (jobs) {
                    if (jobs.poll() == currentJob) {
                        jobs.addLast(currentJob);
                    }
                }

                throw e;
            } finally {
                transactionPruner.saveState();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Stream<TransactionPrunerJob> stream() {
        return jobs.stream();
    }
}
