package com.iota.iri.service.snapshot.impl;

import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.StateDiffViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotMetaData;
import com.iota.iri.service.snapshot.SnapshotState;
import com.iota.iri.service.snapshot.SnapshotStateDiff;
import com.iota.iri.storage.Tangle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

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
     *
     * @see #rollbackLastMilestone(Tangle)
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
    public void replayMilestones(int targetMilestoneIndex, Tangle tangle) throws SnapshotException {
        lockWrite();

        Snapshot snapshotBeforeChanges = new SnapshotImpl(this);

        try {
            for (int currentMilestoneIndex = getIndex() + 1; currentMilestoneIndex <= targetMilestoneIndex;
                 currentMilestoneIndex++) {

                MilestoneViewModel currentMilestone = MilestoneViewModel.get(tangle, currentMilestoneIndex);
                if (currentMilestone != null) {
                    StateDiffViewModel stateDiffViewModel = StateDiffViewModel.load(tangle, currentMilestone.getHash());
                    if(!stateDiffViewModel.isEmpty()) {
                        state.applyStateDiff(new SnapshotStateDiffImpl(stateDiffViewModel.getDiff()));
                    }

                    metaData.setIndex(currentMilestone.index());
                    metaData.setHash(currentMilestone.getHash());

                    TransactionViewModel currentMilestoneTransaction = TransactionViewModel.fromHash(tangle,
                            currentMilestone.getHash());

                    if(currentMilestoneTransaction != null &&
                            currentMilestoneTransaction.getType() != TransactionViewModel.PREFILLED_SLOT) {

                        metaData.setTimestamp(currentMilestoneTransaction.getTimestamp());
                    }
                } else {
                    skippedMilestones.add(currentMilestoneIndex);
                }
            }
        } catch (Exception e) {
            update(snapshotBeforeChanges);

            throw new SnapshotException("failed to replay the the state of the ledger", e);
        } finally {
            unlockWrite();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rollBackMilestones(int targetMilestoneIndex, Tangle tangle) throws SnapshotException {
        if(targetMilestoneIndex <= getInitialIndex() || targetMilestoneIndex > getIndex()) {
            throw new SnapshotException("invalid milestone index");
        }

        lockWrite();

        Snapshot snapshotBeforeChanges = new SnapshotImpl(this);

        try {
            boolean rollbackSuccessful = true;
            while (targetMilestoneIndex <= getIndex() && rollbackSuccessful) {
                rollbackSuccessful = rollbackLastMilestone(tangle);
            }

            if(targetMilestoneIndex < getIndex()) {
                throw new SnapshotException("failed to reach the target milestone index when rolling back milestones");
            }
        } catch(SnapshotException e) {
            update(snapshotBeforeChanges);

            throw e;
        } finally {
            unlockWrite();
        }
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeToDisk(String basePath) throws SnapshotException {
        lockRead();

        try {
            state.writeToDisk(basePath + ".snapshot.state");
            metaData.writeToDisk(basePath + ".snapshot.meta");
        } finally {
            unlockRead();
        }
    }

    /**
     * This method reverts the changes caused by the last milestone that was applied to this snapshot.
     *
     * It first checks if we didn't arrive at the initial index yet and then reverts the balance changes that were
     * caused by the last milestone. Then it checks if any milestones were skipped while applying the last milestone and
     * determines the {@link SnapshotMetaData} that this Snapshot had before and restores it.
     *
     * @param tangle Tangle object which acts as a database interface
     * @return true if the snapshot was rolled back or false otherwise
     * @throws SnapshotException if anything goes wrong while accessing the database
     */
    private boolean rollbackLastMilestone(Tangle tangle) throws SnapshotException {
        if (getIndex() == getInitialIndex()) {
            return false;
        }

        lockWrite();

        try {
            // revert the last balance changes
            StateDiffViewModel stateDiffViewModel = StateDiffViewModel.load(tangle, getHash());
            if (!stateDiffViewModel.isEmpty()) {
                SnapshotStateDiffImpl snapshotStateDiff = new SnapshotStateDiffImpl(
                    stateDiffViewModel.getDiff().entrySet().stream().map(
                        hashLongEntry -> new HashMap.SimpleEntry<>(
                            hashLongEntry.getKey(), -1 * hashLongEntry.getValue()
                        )
                    ).collect(
                        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)
                    )
                );

                if (!snapshotStateDiff.isConsistent()) {
                    throw new SnapshotException("the StateDiff belonging to milestone #" + getIndex() +
                            " (" + getHash() + ") is inconsistent");
                } else if (!state.patchedState(snapshotStateDiff).isConsistent()) {
                    throw new SnapshotException("failed to apply patch belonging to milestone #" + getIndex() +
                            " (" + getHash() + ")");
                }

                state.applyStateDiff(snapshotStateDiff);
            }

            // jump skipped milestones
            int currentIndex = getIndex() - 1;
            while (skippedMilestones.remove(currentIndex)) {
                currentIndex--;
            }

            // check if we arrived at the start
            if (currentIndex <= getInitialIndex()) {
                metaData.setIndex(getInitialIndex());
                metaData.setHash(getInitialHash());
                metaData.setTimestamp(getInitialTimestamp());

                return true;
            }

            // otherwise set metadata of the previous milestone
            MilestoneViewModel currentMilestone = MilestoneViewModel.get(tangle, currentIndex);
            metaData.setIndex(currentMilestone.index());
            metaData.setHash(currentMilestone.getHash());
            metaData.setTimestamp(TransactionViewModel.fromHash(tangle, currentMilestone.getHash()).getTimestamp());

            return true;
        } catch (Exception e) {
            throw new SnapshotException("failed to rollback last milestone", e);
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
