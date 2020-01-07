package com.iota.iri.service.snapshot;

import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.service.transactionpruning.PruningCondition;

/**
 * Represents the manager for local {@link Snapshot}s that takes care of periodically creating a new {@link Snapshot}
 * when the configured interval has passed.
 */
public interface LocalSnapshotManager {
    /**
     * Starts the automatic creation of local {@link Snapshot}s by spawning a background {@link Thread}, that
     * periodically checks if the last snapshot is older than
     * {@link com.iota.iri.conf.SnapshotConfig#getLocalSnapshotsIntervalSynced()}.
     *
     * When we detect that it is time for a local snapshot we internally trigger its creation.
     *
     * Note: If the node is not fully synced we use
     * {@link com.iota.iri.conf.SnapshotConfig#getLocalSnapshotsIntervalUnsynced()} instead.
     *
     * @param latestMilestoneTracker tracker for the milestones to determine when a new local snapshot is due
     */
    void start(LatestMilestoneTracker latestMilestoneTracker);

    /**
     * Stops the {@link Thread} that takes care of creating the local {@link Snapshot}s and that was spawned by the
     * {@link #start(LatestMilestoneTracker)} method.
     */
    void shutdown();
    
    /**
     * Add a conditional check which will be queried every cycle of the manager.
     * 
     * @param conditions conditions on which we check to make a snapshot
     */
    void addSnapshotCondition(SnapshotCondition... conditions);

    /**
     * Add a conditional check for pruning which will be queried every cycle of the manager.
     *
     * @param conditions conditions on which we check to make a snapshot
     */
    void addPruningConditions(PruningCondition... conditions);
}
