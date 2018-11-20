package com.iota.iri.service.transactionpruning.async;

import com.iota.iri.service.transactionpruning.TransactionPrunerJob;
import com.iota.iri.service.transactionpruning.TransactionPruningException;

import java.util.stream.Stream;

/**
 * Represents a queue of jobs which is used by the {@link AsyncTransactionPruner} to internally organize the different
 * kind of jobs.
 */
public interface JobQueue<T extends TransactionPrunerJob> {
    /**
     * Allows to add a job to the queue.
     *
     * @param job job that shall be added to the queue
     */
    void addJob(T job) throws TransactionPruningException;

    /**
     * Clears the stored jobs and resets the queue.
     */
    void clear();

    /**
     * This method processes the entire queue of this job type.
     *
     * @throws TransactionPruningException if anything goes wrong while processing the jobs
     */
    void processJobs() throws TransactionPruningException;

    /**
     * Returns a stream of jobs that can for example be used to serialize the jobs of this queue.
     *
     * @return Stream of jobs that are currently part of this queue
     */
    Stream<T> stream();
}
