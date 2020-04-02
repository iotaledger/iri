package com.iota.iri.service.ledger;

import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * Represents the service that contains all the relevant business logic for modifying and calculating the ledger
 * state.
 * </p>
 * This class is stateless and does not hold any domain specific models.
 */
public interface LedgerService {
    /**
     * <p>
     * Restores the ledger state after a restart of IRI, which allows us to fast forward to the point where we
     * stopped before the restart.
     * </p>
     * <p>
     * It looks for the last solid milestone that was applied to the ledger in the database and then replays all
     * milestones leading up to this point by applying them to the latest snapshot. We do not check every single
     * milestone again but assume that the data in the database is correct. If the database would have any
     * inconsistencies and the application fails, the latest solid milestone tracker will check and apply the milestones
     * one by one and repair the corresponding inconsistencies.
     * </p>
     *
     * @throws LedgerException if anything unexpected happens while trying to restore the ledger state
     */
    void restoreLedgerState() throws LedgerException;

    /**
     * <p>
     * Applies the given milestone to the ledger state.
     * </p>
     * <p>
     * It first marks the transactions that were confirmed by this milestones as confirmed by setting their
     * corresponding {@code snapshotIndex} value. Then it generates the {@link com.iota.iri.model.StateDiff} that
     * reflects the accumulated balance changes of all these transactions and applies it to the latest Snapshot.
     * </p>
     *
     * @param milestone the milestone that shall be applied
     * @return {@code true} if the milestone could be applied to the ledger and {@code false} otherwise
     * @throws LedgerException if anything goes wrong while modifying the ledger state
     */
    boolean applyMilestoneToLedger(MilestoneViewModel milestone) throws LedgerException;

    /**
     * <p>
     * Checks the consistency of the combined balance changes of the given tips.
     * </p>
     * <p>
     * It simply calculates the balance changes of the tips and then combines them to verify that they are leading to a
     * consistent ledger state (which means that they are not containing any double-spends or spends of non-existent
     * IOTA).
     * </p>
     *
     * @param hashes a list of hashes that reference the chosen tips
     * @return {@code true} if the tips are consistent and {@code false} otherwise
     * @throws LedgerException if anything unexpected happens while checking the consistency of the tips
     */
    boolean tipsConsistent(List<Hash> hashes) throws LedgerException;

    /**
     * <p>
     * Checks if the balance changes of the transactions that are referenced by the given tip are consistent.
     * </p>
     * <p>
     * It first calculates the balance changes, then adds them to the given {@code diff} and finally checks their
     * consistency. If we are only interested in the changes that are referenced by the given {@code tip} we need to
     * pass in an empty map for the {@code diff} parameter.
     * </p>
     * <p>
     * The {@code diff} as well as the {@code approvedHashes} parameters are modified, so they will contain the new
     * balance changes and the approved transactions after this method terminates.
     * </p>
     *
     * @param approvedHashes a set of transaction hashes that shall be considered to be approved already (and that
     *                       consequently shall be excluded from the calculation)
     * @param diff a map of balances associated to their address that shall be used as a basis for the balance
     * @param tip the tip that will have its approvees checked
     * @return {@code true} if the balance changes are consistent and {@code false} otherwise
     * @throws LedgerException if anything unexpected happens while determining the consistency
     */
    boolean isBalanceDiffConsistent(Set<Hash> approvedHashes, Map<Hash, Long> diff, Hash tip) throws LedgerException;

    /**
     * <p>
     * Generates the accumulated balance changes of the transactions that are directly or indirectly referenced by the
     * given transaction relative to the referenced milestone. Also persists the spent addresses that were found in the
     * process.
     * </p>
     * <p>
     * It simply iterates over all approvees that have not been confirmed yet and that have not been processed already
     * (by being part of the {@code visitedNonMilestoneSubtangleHashes} set) and collects their balance changes.
     * </p>
     *
     * @param visitedTransactions a set of transaction hashes that shall be considered to be visited already
     * @param startTransaction    the transaction that marks the start of the dag traversal and that has its approvees
     *                            examined
     * @param enforceExtraRules   enforce {@link com.iota.iri.BundleValidator#validateBundleTransactionsApproval(List)}
     *                            and {@link com.iota.iri.BundleValidator#validateBundleTailApproval(Tangle, List)}.
     *                            Enforcing them may break backwards compatibility.
     * @return a map of the balance changes (addresses associated to their balance) or {@code null} if the balance could
     *         not be generated due to inconsistencies
     * @throws LedgerException if anything unexpected happens while generating the balance changes
     */
    Map<Hash, Long> generateBalanceDiff(Set<Hash> visitedTransactions, Hash startTransaction, int milestoneIndex,
            boolean enforceExtraRules)
            throws LedgerException;
}
