package com.iota.iri.service.transactionpruning;

import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.storage.Tangle;

/**
 * Represents the basic contract for a job that get processed by the {@link TransactionPruner}.
 */
public interface TransactionPrunerJob {
    /**
     * Allows to set the {@link TransactionPruner} that this job belongs to (optional).
     *
     * It simply stores the passed in instance, so the job can for example persist the state, once it has finished
     * processing a sub-task. We do not pass this parameter in via the constructor of the job, so we can also test
     * jobs standalone outside the scope of a {@link TransactionPruner}.
     *
     * It gets automatically called and set when we add a job to a {@link TransactionPruner}.
     *
     * @param transactionPruner manager of the job that schedules it execution
     */
    void setTransactionPruner(TransactionPruner transactionPruner);

    /**
     * This method returns the stored {@link TransactionPruner} instance.
     *
     * @return manager of the job that schedules it execution
     */
    TransactionPruner getTransactionPruner();

    /**
     * Allows to set the {@link Tangle} object that this job should work on.
     *
     * We do not pass this parameter in via the constructor of the job because the container, that the job get's added
     * to (the {@link TransactionPruner} already has knowledge about the {@link Tangle} instance we are working on and
     * can automatically set the reference upon addition of the job.
     *
     * This way we reduce the amount of code that has to be written when creating a job and at the same time ensure that
     * all jobs are automatically working on the same correct {@link Tangle} instance, while still keeping full control
     * over the property in tests (where we might want to test a job outside the scope of a {@link TransactionPruner}).
     *
     * @param tangle Tangle object which acts as a database interface
     */
    void setTangle(Tangle tangle);

    /**
     * This method returns the stored {@link Tangle} instance.
     *
     * @return Tangle object which acts as a database interface
     */
    Tangle getTangle();

    /**
     * Allows to set the {@link TipsViewModel} object that this job uses to remove pruned transactions from this cached
     * instance.
     *
     * We do not pass this parameter in via the constructor of the job because the container, that the job get's added
     * to (the {@link TransactionPruner} already has knowledge about the {@link TipsViewModel} instance we are working
     * on and can automatically set the reference upon addition of the job.
     *
     * This way we reduce the amount of code that has to be written when creating a job and at the same time ensure that
     * all jobs are automatically working on the same correct {@link TipsViewModel} instance, while still keeping full
     * control over the property in tests  (where we might want to test a job outside the scope of a
     * {@link TransactionPruner}).
     *
     * @param tipsViewModel manager for the tips (required for removing pruned transactions from this manager)
     */
    void setTipsViewModel(TipsViewModel tipsViewModel);

    /**
     * This method returns the previously set {@link TipsViewModel} instance.
     *
     * @return manager for the tips (required for removing pruned transactions from this manager)
     */
    TipsViewModel getTipsViewModel();

    /**
     * Allows to set the {@link Snapshot} that the node is using as a starting point for the state of the ledger.
     *
     * It can be used to retrieve important information about the range of transactions that are relevant for our node.
     *
     * @param snapshot last local or global snapshot that acts as a starting point for the state of ledger
     */
    void setSnapshot(Snapshot snapshot);

    /**
     * This method returns the previously set {@link Snapshot} instance.
     *
     * @return last local or global snapshot that acts as a starting point for the state of ledger
     */
    Snapshot getSnapshot();

    /**
     * Getter for the execution status of the job.
     *
     * @return execution status of the job.
     */
    TransactionPrunerJobStatus getStatus();

    /**
     * Setter for the execution status of the job.
     *
     * @param transactionPrunerJobStatus new execution status of the job
     */
    void setStatus(TransactionPrunerJobStatus transactionPrunerJobStatus);

    /**
     * This method processes the cleanup job and performs the actual pruning.
     *
     * @throws TransactionPruningException if something goes wrong while processing the job
     */
    void process() throws TransactionPruningException;

    /**
     * This method is used to serialize the job before it gets persisted by the {@link TransactionPruner}.
     *
     * @return string representing a serialized version of this job
     */
    String serialize();
}
