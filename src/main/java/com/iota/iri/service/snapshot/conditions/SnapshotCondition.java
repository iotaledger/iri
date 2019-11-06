package com.iota.iri.service.snapshot.conditions;

import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotException;

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
     * Determines the amount of milestones that should be snapshotted. 
     * Should only be called when {@link #shouldTakeSnapshot(boolean)} returs <code>true</code>
     * 
     * @return The number of the last milestone we will keep in the database. 
     *         Everything before this number will be snapshotted away.
     * @throws SnapshotException if we could not obtain the requirements for determining the snapshot index
     */
    int getSnapshotStartingMilestone() throws SnapshotException;
}
