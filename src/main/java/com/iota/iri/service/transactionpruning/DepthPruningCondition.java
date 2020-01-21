package com.iota.iri.service.transactionpruning;

import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;

/**
 * Initiates pruning based on the number of milestones in the DB
 */
public class DepthPruningCondition implements PruningCondition {

    private final SnapshotConfig config;
    private final SnapshotProvider snapshotProvider;
    private final Tangle tangle;


    /**
     * Initialize a condition to prune based on the number of milestones
     *
     * @param config Snapshot configuration
     * @param snapshotProvider persistence connector to snapshot data
     * @param tangle persistence connector to tangle data
     */
    public DepthPruningCondition(SnapshotConfig config, SnapshotProvider snapshotProvider, Tangle tangle) {
        this.config = config;
        this.snapshotProvider = snapshotProvider;
        this.tangle = tangle;
    }

    @Override
    public boolean shouldPrune() throws TransactionPruningException {
        try {
            MilestoneViewModel milestonevm = MilestoneViewModel.first(tangle);
            return milestonevm != null && getSnapshotPruningMilestone() > milestonevm.index();
        } catch (Exception e) {
            throw new TransactionPruningException("Unable to determine start milestone", e);
        }
    }

    @Override
    public int getSnapshotPruningMilestone() {
        Snapshot initialSnapshot = snapshotProvider.getInitialSnapshot();
        int snapshotIndex = initialSnapshot.getIndex();
        return snapshotIndex - config.getLocalSnapshotsPruningDelay();
    }
}
