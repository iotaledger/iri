package com.iota.iri.service.snapshot.conditions;

import com.google.common.annotations.VisibleForTesting;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;

/**
 * 
 *
 *
 */
public class SnapshotDepthCondition implements SnapshotCondition {

    private final SnapshotConfig config;
    private final SnapshotProvider snapshotProvider;
    private final Tangle tangle;

    /**
     * Implements a {@link SnapshotCondition} based on the amount of milestones we have
     *  
     * @param tangle the database interface.
     * @param config configuration with snapshot specific settings.
     * @param snapshotProvider gives us access to the relevant snapshots.
     */
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
    public int getSnapshotPruningMilestone() throws SnapshotException {
        return snapshotProvider.getLatestSnapshot().getIndex() - config.getLocalSnapshotsPruningDelay();
    }
}
