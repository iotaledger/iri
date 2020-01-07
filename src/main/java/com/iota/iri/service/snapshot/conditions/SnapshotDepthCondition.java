package com.iota.iri.service.snapshot.conditions;

import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.service.snapshot.SnapshotCondition;
import com.iota.iri.service.snapshot.SnapshotProvider;

import com.google.common.annotations.VisibleForTesting;

/**
 * 
 * Conditions for snapshotting based on the distance between the current milestone and the amount we want to keep 
 *
 */
public class SnapshotDepthCondition implements SnapshotCondition {

    private final SnapshotConfig config;
    private final SnapshotProvider snapshotProvider;

    /**
     * Implements a {@link SnapshotCondition} based on the amount of milestones we have
     *
     * @param config configuration with snapshot specific settings.
     * @param snapshotProvider gives us access to the relevant snapshots.
     */
    public SnapshotDepthCondition(SnapshotConfig config, SnapshotProvider snapshotProvider) {
        this.config = config;
        this.snapshotProvider = snapshotProvider;
    }

    @Override
    public boolean shouldTakeSnapshot(boolean isInSync) {
        int localSnapshotInterval = getSnapshotInterval(isInSync);

        int latestSnapshotIndex = snapshotProvider.getLatestSnapshot().getIndex();
        int initialSnapshotIndex = snapshotProvider.getInitialSnapshot().getIndex();

        return latestSnapshotIndex - initialSnapshotIndex > config.getLocalSnapshotsDepth() + localSnapshotInterval;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * It determines the milestone by subtracting the {@link SnapshotConfig#getLocalSnapshotsDepth()} from the latest
     * solid milestone index and retrieving the next milestone before this point.
     * </p>
     */
    @Override
    public int getSnapshotStartingMilestone() {
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

    @Override
    public int getSnapshotPruningMilestone() {
        return snapshotProvider.getLatestSnapshot().getIndex() - config.getLocalSnapshotsPruningDelay();
    }
}
