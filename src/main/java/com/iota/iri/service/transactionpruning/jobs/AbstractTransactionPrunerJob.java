package com.iota.iri.service.transactionpruning.jobs;

import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.transactionpruning.TransactionPruner;
import com.iota.iri.service.transactionpruning.TransactionPrunerJob;
import com.iota.iri.service.transactionpruning.TransactionPrunerJobStatus;
import com.iota.iri.service.transactionpruning.TransactionPruningException;
import com.iota.iri.storage.Tangle;

/**
 * Implements the most basic functionality that is shared by the different kinds of jobs.
 */
public abstract class AbstractTransactionPrunerJob implements TransactionPrunerJob {
    /**
     * Holds the execution status of the job.
     */
    private TransactionPrunerJobStatus status = TransactionPrunerJobStatus.PENDING;

    /**
     * Holds a reference to the manager of the job that schedules it execution.
     */
    private TransactionPruner transactionPruner;

    /**
     * Holds a reference to the tangle object which acts as a database interface.
     */
    private Tangle tangle;

    /**
     * Holds a reference to the manager for the tips (required for removing pruned transactions from this manager).
     */
    private TipsViewModel tipsViewModel;

    /**
     * Holds a reference to the last local or global snapshot that acts as a starting point for the state of ledger.
     */
    private Snapshot snapshot;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTransactionPruner(TransactionPruner transactionPruner) {
        this.transactionPruner = transactionPruner;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TransactionPruner getTransactionPruner() {
        return transactionPruner;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTangle(Tangle tangle) {
        this.tangle = tangle;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Tangle getTangle() {
        return tangle;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTipsViewModel(TipsViewModel tipsViewModel) {
        this.tipsViewModel = tipsViewModel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TipsViewModel getTipsViewModel() {
        return tipsViewModel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSnapshot(Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Snapshot getSnapshot() {
        return snapshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TransactionPrunerJobStatus getStatus() {
        return status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStatus(TransactionPrunerJobStatus status) {
        this.status = status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract void process() throws TransactionPruningException;

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract String serialize();
}
