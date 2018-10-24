package com.iota.iri.service.snapshot;

import com.iota.iri.storage.Tangle;

/**
 * Represents a complete Snapshot of the ledger state.
 *
 * The Snapshot is a container for the {@link SnapshotMetaData} and the {@link SnapshotState} and therefore fulfills
 * both of these contracts while offering some additional utility methods to manipulate them.
 *
 * Important: Since we are only dealing with Snapshots outside of the snapshot package (the underlying meta data and
 *            state objects are not exposed via getters) this class takes care of making the exposed methods thread
 *            safe. The logic of the underlying objects is not thread-safe (for performance and simplicity reasons) but
 *            we don't need to worry about this since we do not have access to them.
 */
public interface Snapshot extends SnapshotMetaData, SnapshotState {
    /**
     * Locks the complete Snapshot object for read access.
     *
     * This is used to synchronize the access from different Threads.
     */
    void lockRead();

    /**
     * Unlocks the complete Snapshot object from read blocks.
     *
     * This is used to synchronize the access from different Threads.
     */
    void unlockRead();

    /**
     * Locks the complete Snapshot object for write access.
     *
     * This is used to synchronize the access from different Threads.
     */
    void lockWrite();

    /**
     * Unlocks the complete Snapshot object from write blocks.
     *
     * This is used to synchronize the access from different Threads.
     */
    void unlockWrite();

    /**
     * This method applies the balance changes that are introduced by future milestones to the current Snapshot.
     *
     * It iterates over the milestone indexes starting from the current index to the target index and applies all found
     * milestone balances. If it can not find a milestone for a certain index it keeps track of these skipped
     * milestones, which allows us to revert the changes even if the missing milestone was received and processed in the
     * mean time. If the application of changes fails, we restore the state of the snapshot to the one it had before the
     * application attempt so this method only modifies the Snapshot if it succeeds.
     *
     * Note: the changes done by this method can be reverted by using {@link #rollBackMilestones(int, Tangle)}
     *
     * @param targetMilestoneIndex the index of the milestone that should be applied
     * @param tangle Tangle object which acts as a database interface
     * @throws SnapshotException if something goes wrong while applying the changes
     */
    void replayMilestones(int targetMilestoneIndex, Tangle tangle) throws SnapshotException;

    /**
     * This method rolls back the latest milestones until it reaches the state that the snapshot had before applying
     * the milestone indicated by the given parameter.
     *
     * When rolling back the milestones we take previously skipped milestones into account, so this method should give
     * the correct result, even if the missing milestones were received and processed in the mean time. If the rollback
     * fails, we restore the state of the snapshot to the one it had before the rollback attempt so this method only
     * modifies the Snapshot if it succeeds.
     *
     * Note: this method is used to reverse the changes introduced by {@link #replayMilestones(int, Tangle)}
     *
     * @param targetMilestoneIndex the index of the milestone that should be rolled back (including all following
     *                             milestones that were applied)
     * @param tangle Tangle object which acts as a database interface
     * @throws SnapshotException if something goes wrong while reverting the changes
     */
    void rollBackMilestones(int targetMilestoneIndex, Tangle tangle) throws SnapshotException;

    /**
     * Replaces the values of this instance with the values of another snapshot object.
     *
     * This can for example be used to "reset" the snapshot after a failed modification attempt (while being able to
     * keep the same instance).
     *
     * @param snapshot the new snapshot details that shall overwrite the current ones
     */
    void update(Snapshot snapshot);

    /**
     * This method dumps the whole snapshot to the hard disk.
     *
     * It is used to persist the in memory state of the snapshot and allow IRI to resume from the local snapshot after
     * restarts.
     *
     * Note: This method writes two files - the meta data file and the state file. The path of the corresponding file is
     *       determined by appending ".snapshot.meta" / ".snapshot.state" to the given base path.
     *
     * @param basePath base path of the local snapshot files
     * @throws SnapshotException if anything goes wrong while writing the file
     */
    @Override
    void writeToDisk(String basePath) throws SnapshotException;
}
