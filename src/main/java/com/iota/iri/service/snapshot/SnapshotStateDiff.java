package com.iota.iri.service.snapshot;

import com.iota.iri.model.Hash;

import java.util.Map;

/**
 * An immutable collection of balance changes that can be used to modify the ledger state.
 */
public interface SnapshotStateDiff {
    /**
     * The {@link Map} returned by this method is a copy which guarantees the immutability of this object.
     *
     * @return the balance changes associated to their address hash
     */
    Map<Hash, Long> getBalanceChanges();

    /**
     * Checks if the balance changes are consistent, which means that the sum of all changes is exactly zero.
     *
     * @return true if the sum of all balances is zero
     */
    boolean isConsistent();
}
