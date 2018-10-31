package com.iota.iri.service.snapshot.impl;

import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.SnapshotState;
import com.iota.iri.service.snapshot.SnapshotStateDiff;

import java.util.HashMap;
import java.util.Map;

/**
 * Implements the basic contract of the {@link SnapshotStateDiff} interface.
 */
public class SnapshotStateDiffImpl implements SnapshotStateDiff {
    /**
     * Holds the addresses associated to their corresponding balance change.
     */
    private final Map<Hash, Long> balanceChanges;

    /**
     * Creates an object that can be used to patch the {@link SnapshotState} and examine the consistency of the changes.
     *
     * @param balanceChanges map with the addresses and their balance changes
     */
    public SnapshotStateDiffImpl(Map<Hash, Long> balanceChanges) {
        this.balanceChanges = balanceChanges;
    }

    /**
     * Creates a deep clone of the passed in {@link SnapshotStateDiff}.
     *
     * @param snapshotStateDiff object that shall be cloned
     */
    public SnapshotStateDiffImpl(SnapshotStateDiff snapshotStateDiff) {
        this(snapshotStateDiff.getBalanceChanges());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Hash, Long> getBalanceChanges() {
        return new HashMap<>(balanceChanges);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConsistent() {
        return balanceChanges.entrySet()
                .stream()
                .map(Map.Entry::getValue)
                .reduce(Math::addExact)
                .orElse(0L)
                .equals(0L);
    }
}
