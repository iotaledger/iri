package com.iota.iri.service.snapshot.impl;

import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotMetaData;
import com.iota.iri.service.snapshot.SnapshotState;
import com.iota.iri.service.snapshot.SnapshotStateDiff;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implements the basic contract of the {@link Snapshot} interface.
 */
public class SnapshotImpl implements Snapshot {
    /**
     * Holds a reference to the state of this snapshot.
     */
    private final SnapshotState state;

    /**
     * Holds a reference to the metadata of this snapshot.
     */
    private final SnapshotMetaData metaData;

    /**
     * Lock object allowing to block access to this object from different threads.
     */
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    /**
     * Holds a set of milestones indexes that were skipped while advancing the Snapshot state.
     *
     * It is used to be able to identify which milestones have to be rolled back, even when additional milestones have
     * become known in the mean time.
     */
    private Set<Integer> skippedMilestones = new HashSet<>();

    /**
    /**
     * Creates a snapshot object with the given information.
     *
     * It simply stores the passed in parameters in the internal properties.
     *
     * @param state state of the snapshot with the balances of all addresses
     * @param metaData meta data of the snapshot with the additional information of this snapshot
     */
    public SnapshotImpl(SnapshotState state, SnapshotMetaData metaData) {
        this.state = state;
        this.metaData = metaData;
    }

    /**
     * Creates a deep clone of the passed in {@link Snapshot}.
     *
     * @param snapshot object that shall be cloned
     */
    public SnapshotImpl(Snapshot snapshot) {
        this(
            new SnapshotStateImpl(((SnapshotImpl) snapshot).state),
            new SnapshotMetaDataImpl(((SnapshotImpl) snapshot).metaData)
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void lockRead() {
        readWriteLock.readLock().lock();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unlockRead() {
        readWriteLock.readLock().unlock();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void lockWrite() {
        readWriteLock.writeLock().lock();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unlockWrite() {
        readWriteLock.writeLock().unlock();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean addSkippedMilestone(int skippedMilestoneIndex) {
        return skippedMilestones.add(skippedMilestoneIndex);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeSkippedMilestone(int skippedMilestoneIndex) {
        return skippedMilestones.remove(skippedMilestoneIndex);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(Snapshot snapshot) {
        lockWrite();

        try {
            state.update(((SnapshotImpl) snapshot).state);
            metaData.update(((SnapshotImpl) snapshot).metaData);
        } finally {
            unlockWrite();
        }
    }

    //region [THREAD-SAFE METADATA METHODS] ////////////////////////////////////////////////////////////////////////////


    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public Hash getInitialHash() {
        lockRead();

        try {
            return metaData.getInitialHash();
        } finally {
            unlockRead();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public void setInitialHash(Hash initialHash) {
        lockWrite();

        try {
            metaData.setInitialHash(initialHash);
        } finally {
            unlockWrite();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public int getInitialIndex() {
        lockRead();

        try {
            return metaData.getInitialIndex();
        } finally {
            unlockRead();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public void setInitialIndex(int initialIndex) {
        lockWrite();

        try {
            metaData.setInitialIndex(initialIndex);
        } finally {
            unlockWrite();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public long getInitialTimestamp() {
        lockRead();

        try {
            return metaData.getInitialTimestamp();
        } finally {
            unlockRead();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public void setInitialTimestamp(long initialTimestamp) {
        lockWrite();

        try {
            metaData.setInitialTimestamp(initialTimestamp);
        } finally {
            unlockWrite();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public Hash getHash() {
        lockRead();

        try {
            return this.metaData.getHash();
        } finally {
            unlockRead();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public void setHash(Hash hash) {
        lockWrite();

        try {
            metaData.setHash(hash);
        } finally {
            unlockWrite();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public int getIndex() {
        lockRead();

        try {
            return metaData.getIndex();
        } finally {
            unlockRead();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public void setIndex(int index) {
        lockWrite();

        try {
            metaData.setIndex(index);
        } finally {
            unlockWrite();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public long getTimestamp() {
        lockRead();

        try {
            return metaData.getTimestamp();
        } finally {
            unlockRead();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public void setTimestamp(long timestamp) {
        lockWrite();

        try {
            metaData.setTimestamp(timestamp);
        } finally {
            unlockWrite();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public Map<Hash, Integer> getSolidEntryPoints() {
        lockRead();

        try {
            return metaData.getSolidEntryPoints();
        } finally {
            unlockRead();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public void setSolidEntryPoints(Map<Hash, Integer> solidEntryPoints) {
        lockWrite();

        try {
            metaData.setSolidEntryPoints(solidEntryPoints);
        } finally {
            unlockWrite();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public boolean hasSolidEntryPoint(Hash solidEntrypoint) {
        lockRead();

        try {
            return metaData.hasSolidEntryPoint(solidEntrypoint);
        } finally {
            unlockRead();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public int getSolidEntryPointIndex(Hash solidEntrypoint) {
        lockRead();

        try {
            return metaData.getSolidEntryPointIndex(solidEntrypoint);
        } finally {
            unlockRead();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public Map<Hash, Integer> getSeenMilestones() {
        lockRead();

        try {
            return metaData.getSeenMilestones();
        } finally {
            unlockRead();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public void setSeenMilestones(Map<Hash, Integer> seenMilestones) {
        lockWrite();

        try {
            metaData.setSeenMilestones(seenMilestones);
        } finally {
            unlockWrite();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotMetaData} method.
     */
    @Override
    public void update(SnapshotMetaData newMetaData) {
        lockWrite();

        try {
            metaData.update(newMetaData);
        } finally {
            unlockWrite();
        }
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region [THREAD-SAFE STATE METHODS] ///////////////////////////////////////////////////////////////////////////////

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotState} method.
     */
    @Override
    public Long getBalance(Hash address) {
        lockRead();

        try {
            return state.getBalance(address);
        } finally {
            unlockRead();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotState} method.
     */
    @Override
    public Map<Hash, Long> getBalances() {
        lockRead();

        try {
            return state.getBalances();
        } finally {
            unlockRead();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotState} method.
     */
    @Override
    public boolean isConsistent() {
        lockRead();

        try {
            return state.isConsistent();
        } finally {
            unlockRead();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotState} method.
     */
    @Override
    public boolean hasCorrectSupply() {
        lockRead();

        try {
            return state.hasCorrectSupply();
        } finally {
            unlockRead();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotState} method.
     */
    @Override
    public void update(SnapshotState newState) {
        lockWrite();

        try {
            state.update(newState);
        } finally {
            unlockWrite();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotState} method.
     */
    @Override
    public void applyStateDiff(SnapshotStateDiff diff) throws SnapshotException {
        lockWrite();

        try {
            state.applyStateDiff(diff);
        } finally {
            unlockWrite();
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is a thread-safe wrapper for the underlying {@link SnapshotState} method.
     */
    @Override
    public SnapshotState patchedState(SnapshotStateDiff snapshotStateDiff) {
        lockRead();

        try {
            return state.patchedState(snapshotStateDiff);
        } finally {
            unlockRead();
        }
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////
}
