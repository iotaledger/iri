package com.iota.iri.service.snapshot;

/**
 * 
 * A snapshot condition defines a limitation our database takes on storing data.
 * Once the condition is met, a {@link Snapshot} will be taken
 *
 */
public interface SnapshotCondition {

    /**
     * Executes a check on the node to determine if we should take a snapshot.
     * 
     * @param isInSync <code>true</code> if this node is considered in sync, in all other cases <code>false</code>
     * @return <code>true</code> if we should take a snapshot, otherwise <code>false</code>
     */
    boolean shouldTakeSnapshot(boolean isInSync);
    
    /**
     * Determines the Milestone index from which we should snapshot.
     * Should only be called when {@link #shouldTakeSnapshot(boolean)} returns <code>true</code>
     * 
     * @return The index of the milestone we will create a new Snapshot at
     * @throws SnapshotException if we could not obtain the requirements for determining the milestone index
     */
    int getSnapshotStartingMilestone() throws SnapshotException;
    
    /**
     * Determines the milestone at which we should start pruning. 
     * This does not take the minimum amount of milestones in the database into account.
     * 
     * @return The index of the last milestone we will keep in the database. 
     *         Everything before this number will be pruned away if allowed.
     * @throws SnapshotException if we could not obtain the requirements for determining the milestone index
     */
    int getSnapshotPruningMilestone() throws SnapshotException;
}
