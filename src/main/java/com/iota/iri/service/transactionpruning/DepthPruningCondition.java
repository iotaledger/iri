package com.iota.iri.service.transactionpruning;

import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;

public class DepthPruningCondition implements PruningCondition {

    private final SnapshotConfig config;
    private final SnapshotProvider snapshotProvider;
    private final Tangle tangle;

    public DepthPruningCondition(SnapshotConfig config, SnapshotProvider snapshotProvider, Tangle tangle) {
        this.config = config;
        this.snapshotProvider = snapshotProvider;
        this.tangle = tangle;
    }

    @Override
    public boolean shouldPrune() throws TransactionPruningException {
        try {
            return getSnapshotPruningMilestone() > MilestoneViewModel.first(tangle).index();
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
