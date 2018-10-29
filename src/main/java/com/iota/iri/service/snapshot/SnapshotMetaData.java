package com.iota.iri.service.snapshot;

import com.iota.iri.model.Hash;

import java.util.Map;

/**
 * Represents the meta data of a snapshot.
 *
 * Since a snapshot represents the state of the ledger at a given point and this point is defined by a chosen milestone
 * in the tangle, we store milestone specific values like a hash, an index and the timestamp but also derived values
 * that are only relevant for the local snapshots logic.
 */
public interface SnapshotMetaData {
    /**
     * Getter of the hash of the transaction that the snapshot was "derived" from.
     *
     * In case of the "latest" {@link Snapshot} this value will be the hash of the "initial" {@link Snapshot}.
     *
     * Note: a snapshot can be modified over time, as we apply the balance changes caused by consecutive transactions,
     *       so this value differs from the value returned by {@link #getHash()}.
     *
     * @return hash of the transaction that the snapshot was "derived" from
     */
    Hash getInitialHash();

    /**
     * Setter of the hash of the transaction that the snapshot was "derived" from.
     *
     * Note: After creating a new local {@link Snapshot} we update this value in the latest {@link Snapshot} to
     *       correctly reflect the new "origin" of the latest {@link Snapshot}.
     *
     * @param initialHash hash of the transaction that the snapshot was "derived" from
     */
    void setInitialHash(Hash initialHash);

    /**
     * Getter of the index of the milestone that the snapshot was "derived" from.
     *
     * In case of the "latest" {@link Snapshot} this value will be the index of the "initial" {@link Snapshot}.
     *
     * Note: a snapshot can be modified over time, as we apply the balance changes caused by consecutive transactions,
     *       so this value differs from the value returned by {@link #getIndex()}.
     *
     * @return index of the milestone that the snapshot was "derived" from
     */
    int getInitialIndex();

    /**
     * Setter of the index of the milestone that the snapshot was "derived" from.
     *
     * Note: After creating a new local {@link Snapshot} we update this value in the latest {@link Snapshot} to
     *       correctly reflect the new "origin" of the latest {@link Snapshot}.
     *
     * @param initialIndex index of the milestone that the snapshot was "derived" from
     */
    void setInitialIndex(int initialIndex);

    /**
     *  Getter of the timestamp of the transaction that the snapshot was "derived" from.
     *
     *  In case of the "latest" {@link Snapshot} this value will be the timestamp of the "initial" {@link Snapshot}.
     *
     * Note: a snapshot can be modified over time, as we apply the balance changes caused by consecutive transactions,
     *       so this value differs from the value returned by {@link #getTimestamp()}.
     *
     * @return timestamp of the transaction that the snapshot was "derived" from
     */
    long getInitialTimestamp();

    /**
     * Setter of the timestamp of the transaction that the snapshot was "derived" from.
     *
     * Note: After creating a new local {@link Snapshot} we update this value in the latest {@link Snapshot} to
     *       correctly reflect the new "origin" of the latest {@link Snapshot}.
     *
     * @param initialTimestamp timestamp of the transaction that the snapshot was "derived" from
     */
    void setInitialTimestamp(long initialTimestamp);

    /**
     * Getter of the hash of the transaction that the snapshot currently belongs to.
     *
     * @return hash of the transaction that the snapshot currently belongs to
     */
    Hash getHash();

    /**
     * Setter of the hash of the transaction that the snapshot currently belongs to.
     *
     * @param hash hash of the transaction that the snapshot currently belongs to
     */
    void setHash(Hash hash);

    /**
     * Getter of the milestone index that the snapshot currently belongs to.
     *
     * Note: At the moment we use milestones as a reference for snapshots.
     *
     * @return milestone index that the snapshot currently belongs to
     */
    int getIndex();

    /**
     * Setter of the milestone index that the snapshot currently belongs to.
     *
     * Note: At the moment we use milestones as a reference for snapshots.
     *
     * @param index milestone index that the snapshot currently belongs to
     */
    void setIndex(int index);

    /**
     * Getter of the timestamp of the transaction that the snapshot currently belongs to.
     *
     * @return timestamp of the transaction that the snapshot currently belongs to
     */
    long getTimestamp();

    /**
     * Setter of the timestamp of the transaction that the snapshot currently belongs to.
     *
     * @param timestamp timestamp of the transaction that the snapshot currently belongs to
     */
    void setTimestamp(long timestamp);

    /**
     * Getter for the solid entry points of the snapshot.
     *
     * A solid entry point is a confirmed transaction that had non-orphaned approvers during the time of the snapshot
     * creation and therefore a connection to the most recent part of the tangle. The solid entry points allow us to
     * stop the solidification process without having to go all the way back to the genesis.
     *
     * Note: Nodes that do not use local snapshots will only have one solid entry point (the NULL_HASH).
     *
     * @return map with the transaction hashes associated to their milestone index
     */
    Map<Hash, Integer> getSolidEntryPoints();

    /**
     * Setter for the solid entry points of this snapshot.
     *
     * A solid entry point is a confirmed transaction that had non-orphaned approvers during the time of the snapshot
     * creation and therefore a connection to the most recent part of the tangle. The solid entry points allow us to
     * stop the solidification process without having to go all the way back to the genesis.
     *
     * Note: Nodes that do not use local snapshots should only have one solid entry point (the NULL_HASH).
     *
     * @param solidEntryPoints map with the transaction hashes associated to their milestone index
     */
    void setSolidEntryPoints(Map<Hash, Integer> solidEntryPoints);

    /**
     * This method checks if a given transaction is a solid entry point.
     *
     * It simply performs a member check on the value returned by {@link #getSolidEntryPoints()} and therefore is a
     * utility method that makes it easier to check a single transaction.
     *
     * A solid entry point is a confirmed transaction that had non-orphaned approvers during the time of the snapshot
     * creation and therefore a connection to the most recent part of the tangle. The solid entry points allow us to
     * stop the solidification process without having to go all the way back to the genesis.
     *
     * @param solidEntrypoint transaction hash that shall be checked
     * @return true if the hash is a solid entry point and false otherwise
     */
    boolean hasSolidEntryPoint(Hash solidEntrypoint);

    /**
     * Returns the index of the milestone that was responsible for confirming the transaction that is now a solid
     * entry point.
     *
     * A solid entry point is a confirmed transaction that had non-orphaned approvers during the time of the snapshot
     * creation and therefore a connection to the most recent part of the tangle. The solid entry points allow us to
     * stop the solidification process without having to go all the way back to the genesis.
     *
     * @param solidEntrypoint transaction hash of the solid entry point
     * @return index of the milestone that once confirmed the solid entry point or null if the given transaction is no
     *         solid entry point
     */
    int getSolidEntryPointIndex(Hash solidEntrypoint);

    /**
     * Getter for the seen milestones.
     *
     * Note: Since new nodes use local snapshot files as a way to bootstrap their database, we store the hashes of all
     *       milestones that have been following the snapshot in its meta data. This allows these nodes to immediately
     *       start requesting the missing milestones without having to discover them one by one, which massively
     *       increases the sync speed of new nodes.
     *
     * @return map of milestone transaction hashes associated to their milestone index
     */
    Map<Hash, Integer> getSeenMilestones();

    /**
     * Setter for the seen milestones.
     *
     * Note: Since new nodes use local snapshot files as a way to bootstrap their database, we store the hashes of all
     *       milestones that have been following the snapshot in its meta data. This allows these nodes to immediately
     *       start requesting the missing milestones without having to discover them one by one, which massively
     *       increases the sync speed of new nodes.
     *
     * @param seenMilestones map of milestone transaction hashes associated to their milestone index
     */
    void setSeenMilestones(Map<Hash, Integer> seenMilestones);

    /**
     * Replaces the meta data values of this instance with the values of another meta data object.
     *
     * This can for example be used to "reset" the meta data after a failed modification attempt (while being able to
     * keep the same instance).
     *
     * @param newMetaData the new meta data that shall overwrite the current one
     */
    void update(SnapshotMetaData newMetaData);
}
