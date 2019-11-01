package com.iota.iri.service.snapshot.conditions;

import com.google.common.annotations.VisibleForTesting;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;

public class SnapshotDepthCondition implements SnapshotCondition {

    private final SnapshotConfig config;
    private final SnapshotProvider snapshotProvider;
    private final Tangle tangle;

    public SnapshotDepthCondition(Tangle tangle, SnapshotConfig config, SnapshotProvider snapshotProvider) {
        this.config = config;
        this.snapshotProvider = snapshotProvider;
        this.tangle = tangle;
    }

    @Override
    public boolean shouldTakeSnapshot(boolean isInSync) {
        int localSnapshotInterval = getSnapshotInterval(isInSync);

        int latestSnapshotIndex = snapshotProvider.getLatestSnapshot().getIndex();
        int initialSnapshotIndex = snapshotProvider.getInitialSnapshot().getIndex();

        return latestSnapshotIndex - initialSnapshotIndex > config.getLocalSnapshotsDepth() + localSnapshotInterval;
    }

    /**
     * <p>
     * This method determines the milestone that shall be used for the local snapshot.
     * </p>
     * <p>
     * It determines the milestone by subtracting the {@link SnapshotConfig#getLocalSnapshotsDepth()} from the latest
     * solid milestone index and retrieving the next milestone before this point.
     * </p>
     * 
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider data provider for the {@link Snapshot}s that are relevant for the node
     * @param config important snapshot related configuration parameters
     * @return the target milestone for the local snapshot
     * @throws SnapshotException if anything goes wrong while determining the target milestone for the local snapshot
     */
    @Override
    public int getSnapshotStartingMilestone() throws SnapshotException {
        int targetMilestoneIndex = snapshotProvider.getLatestSnapshot().getIndex() - config.getLocalSnapshotsDepth();

        MilestoneViewModel targetMilestone;
        try {
            targetMilestone = MilestoneViewModel.findClosestPrevMilestone(tangle, targetMilestoneIndex,
                    snapshotProvider.getInitialSnapshot().getIndex());
        } catch (Exception e) {
            throw new SnapshotException("could not load the target milestone", e);
        }
        if (targetMilestone == null) {
            throw new SnapshotException("missing milestone with an index of " + targetMilestoneIndex + " or lower");
        }

        return snapshotProvider.getLatestSnapshot().getIndex() - config.getLocalSnapshotsDepth();
    }

    /**
     * A snapshot is taken in an interval. 
     * This interval changes based on the state of the node.
     * 
     * @param inSync if this node is in sync
     * @return the current interval in which we take local snapshots
     */
    @VisibleForTesting
    int getSnapshotInterval(boolean inSync) {
        return inSync
                ? config.getLocalSnapshotsIntervalSynced()
                : config.getLocalSnapshotsIntervalUnsynced();
    }
}
