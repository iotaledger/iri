package com.iota.iri.service.snapshot;

import com.iota.iri.model.Hash;

import java.util.Map;

/**
 * Represents the "state" of the ledger at a given time, which means how many IOTA are available on a certain address.
 *
 * It can either be a full ledger state which is used by the Snapshots or a differential State which carries only the
 * resulting balances of the changed balances (see {@link #patchedState(SnapshotStateDiff)}).
 */
public interface SnapshotState {
    /**
     * Returns the balance of a single address.
     *
     * @param address address that shall be retrieved
     * @return balance of the address or {@code null} if the address is unknown.
     */
    Long getBalance(Hash address);

    /**
     * The {@link Map} returned by this method is a copy of the current state which guarantees that the object is only
     * modified through its class methods.
     *
     * @return map with the addresses associated to their balance
     */
    Map<Hash, Long> getBalances();

    /**
     * Checks if the state is consistent, which means that there are no addresses with a negative balance.
     *
     * @return true if the state is consistent and false otherwise
     */
    boolean isConsistent();

    /**
     * Checks if the state of the ledger has the correct supply by adding the balances of all addresses and comparing it
     * against the expected value.
     *
     * It doesn't make sense to call this functions on differential states (returned by {@link
     * #patchedState(SnapshotStateDiff)} that are used to check the consistency of patches.
     *
     * @return true if the supply is correct and false otherwise
     */
    boolean hasCorrectSupply();

    /**
     * Replaces the state of this instance with the values of another state.
     *
     * This can for example be used to "reset" the state after a failed modification attempt while being able to keep
     * the same instance.
     *
     * @param newState the new state that shall overwrite the current one
     */
    void update(SnapshotState newState);

    /**
     * This method applies the given {@link SnapshotStateDiff} to the current balances.
     *
     * Before applying the {@link SnapshotStateDiff} we check if it is consistent.
     *
     * @param diff the balance changes that should be applied to this state.
     * @throws SnapshotException if the {@link SnapshotStateDiff} is inconsistent (see {@link
     *         SnapshotStateDiff#isConsistent()})
     */
    void applyStateDiff(SnapshotStateDiff diff) throws SnapshotException;

    /**
     * This method creates a differential SnapshotState that contains the resulting balances of only the addresses
     * that are modified by the given {@link SnapshotStateDiff}.
     *
     * It can be used to check if the modifications by a {@link SnapshotStateDiff} will result in a consistent State
     * where all modified addresses are still positive. Even though this State can be consistent, it will most probably
     * not return true if we call {@link #hasCorrectSupply()} since the unmodified addresses are missing.
     *
     * @param snapshotStateDiff the balance patches that we want to apply
     * @return a differential SnapshotState that contains the resulting balances of all modified addresses
     */
    SnapshotState patchedState(SnapshotStateDiff snapshotStateDiff);
}
