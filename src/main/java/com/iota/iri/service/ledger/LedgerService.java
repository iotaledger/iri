package com.iota.iri.service.ledger;

import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.zmq.MessageQ;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents the service that contains all the relevant business logic for modifying and calculating the ledger
 * state.<br />
 * <br />
 * This class is stateless and does not hold any domain specific models.<br />
 */
public interface LedgerService {
    /**
     * This method applies the given milestone to the ledger state.<br />
     * <br />
     * It first marks the transactions that were confirmed by this milestones as confirmed by setting their
     * corresponding {@code snapshotIndex} value. Then it generates the {@link com.iota.iri.model.StateDiff} that
     * reflects the accumulated balance changes of all these transactions and applies it to the latest Snapshot.<br />
     *
     * @param tangle Tangle object which acts as a database interface [dependency]
     * @param snapshotProvider snapshot provider which gives us access to the relevant snapshots [dependency]
     * @param messageQ ZeroMQ interface that allows us to emit messages for external recipients [dependency]
     * @param milestone the milestone that shall be applied
     * @return {@code true} if the milestone could be applied to the ledger and {@code false} otherwise
     * @throws LedgerException if anything goes wrong while modifying the ledger state
     */
    boolean applyMilestoneToLedger(Tangle tangle, SnapshotProvider snapshotProvider, MessageQ messageQ,
            MilestoneViewModel milestone) throws LedgerException;

    /**
     * This method checks the consistency of the combined balance changes of the given tips.<br />
     * <br />
     * It simply calculates the balance changes of the tips and then combines them to verify that they are leading to a
     * consistent ledger state (which means that they are not containing any double-spends or spends of non-existent
     * IOTA).<br />
     *
     * @param tangle Tangle object which acts as a database interface [dependency]
     * @param snapshotProvider snapshot provider which gives us access to the relevant snapshots [dependency]
     * @param hashes a list of hashes that reference the chosen tips
     * @return {@code true} if the tips are consistent and {@code false} otherwise
     * @throws LedgerException if anything unexpected happens while checking the consistency of the tips
     */
    boolean tipsConsistent(Tangle tangle, SnapshotProvider snapshotProvider, List<Hash> hashes) throws
            LedgerException;

    /**
     * This method checks if the balance changes of the transactions that are referenced by the given tip are
     * consistent.<br />
     * <br />
     * It first calculates the balance changes, then adds them to the given {@code diff} and finally checks their
     * consistency. If we are only interested in the changes that are referenced by the given {@code tip} we need to
     * pass in an empty map for the {@code diff} parameter.<br />
     * <br />
     * The {@code diff} as well as the {@code approvedHashes} parameters are modified, so they will contain the new
     * balance changes and the approved transactions after this method terminates.<br />
     *
     * @param tangle Tangle object which acts as a database interface [dependency]
     * @param snapshotProvider snapshot provider which gives us access to the relevant snapshots [dependency]
     * @param approvedHashes a set of transaction hashes that shall be considered to be approved already (and that
     *                       consequently shall be excluded from the calculation)
     * @param diff a map of balances associated to their address that shall be used as a basis for the balance
     * @param tip the tip that will have its approvees checked
     * @return {@code true} if the balance changes are consistent and {@code false} otherwise
     * @throws LedgerException if anything unexpected happens while determining the consistency
     */
    boolean isBalanceDiffConsistent(Tangle tangle, SnapshotProvider snapshotProvider, Set<Hash> approvedHashes,
            final Map<Hash, Long> diff, Hash tip) throws LedgerException;

    /**
     * This method generates the accumulated balance changes of the transactions that are "approved" by the given
     * milestone.<br />
     * <br />
     * It simply iterates over all approvees that still belong to the given milestone and that have not been
     * processed already (by being part of the {@code visitedNonMilestoneSubtangleHashes} set) and collects their
     * balance changes.<br />
     *
     * @param tangle Tangle object which acts as a database interface [dependency]
     * @param snapshotProvider snapshot provider which gives us access to the relevant snapshots [dependency]
     * @param visitedTransactions a set of transaction hashes that shall be considered to be visited already
     * @param milestoneHash the milestone  that is examined regarding its approved transactions
     * @param milestoneIndex the milestone index
     * @return a map of the balance changes (addresses associated to their balance) or {@code null} if the balance could
     *         not be generated due to inconsistencies
     * @throws LedgerException if anything unexpected happens while generating the balance changes
     */
    Map<Hash, Long> generateBalanceDiff(Tangle tangle, SnapshotProvider snapshotProvider,
            Set<Hash> visitedTransactions, Hash milestoneHash, int milestoneIndex) throws LedgerException;
}
