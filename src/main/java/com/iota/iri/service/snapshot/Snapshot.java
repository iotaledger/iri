package com.iota.iri.service.snapshot;

/**
 * <p>
 * Represents a complete Snapshot of the ledger state.
 * </p>
 * <p>
 * The Snapshot is a container for the {@link SnapshotMetaData} and the {@link SnapshotState} and therefore fulfills
 * both of these contracts while offering some additional utility methods to manipulate them.
 * </p>
 * <p>
 * Important: Since we are only dealing with Snapshots outside of the snapshot package (the underlying meta data and
 *            state objects are not exposed via getters) this class takes care of making the exposed methods thread
 *            safe. The logic of the underlying objects is not thread-safe (for performance and simplicity reasons) but
 *            we don't need to worry about this since we do not have access to them.
 * </p>
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
     * This methods allows us to keep track when we skip a milestone when applying changes.
     *
     * <p>
     * Since we can only rollback changes if we know which milestones have lead to the current state, we need to keep
     * track of the milestones that were previously skipped.
     * </p>
     *
     * @param skippedMilestoneIndex index of the milestone that was skipped while applying the ledger state
     * @return true if the index was added and false if it was already part of the set
     */
    boolean addSkippedMilestone(int skippedMilestoneIndex);

    /**
     * This methods allows us to remove a milestone index from the internal list of skipped milestone indexes.
     *
     * <p>
     * Since we can only rollback changes if we know which milestones have lead to the current state, we need to keep
     * track of the milestones that were previously skipped.
     * </p>
     *
     * @param skippedMilestoneIndex index of the milestone that was skipped while applying the ledger state
     * @return true if the skipped milestone was removed from the internal list and false if it was not present
     */
    boolean removeSkippedMilestone(int skippedMilestoneIndex);

    /**
     * Replaces the values of this instance with the values of another snapshot object.
     *
     * <p>
     * This can for example be used to "reset" the snapshot after a failed modification attempt (while being able to
     * keep the same instance).
     * </p>
     *
     * @param snapshot the new snapshot details that shall overwrite the current ones
     */
    void update(Snapshot snapshot);

    /**
     * <p>
     * Creates a deep clone of the Snapshot which can be modified without affecting the values of the original
     * one.
     * </p>
     * <p>
     * Since the data structures inside the Snapshot are extremely big, this method is relatively expensive and should
     * only be used when it is really necessary.
     * </p>
     *
     * @return a deep clone of the snapshot
     */
    Snapshot clone();
}
