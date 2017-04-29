package com.iota.iri;

import com.iota.iri.controllers.*;
import com.iota.iri.model.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.iota.iri.Snapshot.latestSnapshot;

/**
 * Created by paul on 4/15/17.
 */
public class LedgerValidator {

    private static final Logger log = LoggerFactory.getLogger(LedgerValidator.class);
    private static final Object updateSyncObject = new Object();
    private static final Snapshot stateSinceMilestone = new Snapshot(latestSnapshot);
    private static final Set<Hash> approvedHashes = new HashSet<>();
    private static volatile int numberOfConfirmedTransactions;

    /**
     * Returns a Map of Address and change in balance that can be used to build a new Snapshot state.
     * Under certain conditions, it will return null:
     *  - While descending through transactions, if a transaction is marked as {PREFILLED_SLOT}, then its hash has been
     *    referenced by some transaction, but the transaction data is not found in the database. It notifies
     *    TransactionRequester to increase the probability this transaction will be present the next time this is checked.
     *  - When a transaction marked as a tail transaction (if the current index is 0), but it is not the first transaction
     *    in any of the BundleValidator's transaction lists, then the bundle is marked as invalid, deleted, and re-requested.
     *  - When the bundle is not internally consistent (the sum of all transactions in the bundle must be zero)
     * As transactions are being traversed, it will come upon bundles, and will add the transaction value to {state}.
     * If {milestone} is true, it will search, through trunk and branch, all transactions, starting from {tip},
     * until it reaches a transaction that is marked as a "snapshot" transaction.
     * If {milestone} is false, it will search up until it reaches a snapshot, or until it finds a hash that has been
     * marked as consistent since the previous milestone.
     * @param tip       the hash of a transaction to start the search from
     * @param milestone marker to indicate whether to stop only at snapshot
     * @return {state}  the addresses that have a balance changed since the last diff check
     * @throws Exception
     */
    private static Map<Hash,Long> getLatestDiff(Hash tip, boolean milestone) throws Exception {
        Map<Hash, Long> state = new HashMap<>();
        int numberOfAnalyzedTransactions = 0;
        Set<Hash> analyzedTips = new HashSet<>(Collections.singleton(Hash.NULL_HASH));
        Set<Hash> countedTx = new HashSet<>(Collections.singleton(Hash.NULL_HASH));

        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
        Hash transactionPointer;
        while ((transactionPointer = nonAnalyzedTransactions.poll()) != null) {

            if (analyzedTips.add(transactionPointer) && (milestone || !approvedHashes.contains(transactionPointer))) {

                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(transactionPointer);
                if (!transactionViewModel.hasSnapshot()) {
                    numberOfAnalyzedTransactions++;
                    if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
                        TransactionRequester.instance().requestTransaction(transactionViewModel.getHash(), milestone);
                        return null;

                    } else {

                        if (transactionViewModel.getCurrentIndex() == 0) {

                            boolean validBundle = false;

                            final BundleValidator bundleValidator = new BundleValidator(transactionViewModel.getBundle());
                            for (final List<TransactionViewModel> bundleTransactionViewModels : bundleValidator.getTransactions()) {

                                if (bundleTransactionViewModels.get(0).getHash().equals(transactionViewModel.getHash())) {

                                    validBundle = true;

                                    for (final TransactionViewModel bundleTransactionViewModel : bundleTransactionViewModels) {

                                        if (bundleTransactionViewModel.value() != 0 && countedTx.add(bundleTransactionViewModel.getHash())) {

                                            final Hash address = bundleTransactionViewModel.getAddress().getHash();
                                            final Long value = state.get(address);
                                            state.put(address, value == null ? bundleTransactionViewModel.value()
                                                    : (value + bundleTransactionViewModel.value()));
                                        }
                                    }

                                    break;
                                }
                            }

                            if (!validBundle || bundleValidator.isInconsistent()) {
                                for(TransactionViewModel transactionViewModel1: bundleValidator.getTransactionViewModels()) {
                                    transactionViewModel1.delete();
                                    TransactionRequester.instance().requestTransaction(transactionViewModel.getHash(), milestone);
                                }
                                return null;
                            }
                        }

                        nonAnalyzedTransactions.offer(transactionViewModel.getTrunkTransactionHash());
                        nonAnalyzedTransactions.offer(transactionViewModel.getBranchTransactionHash());
                    }
                }
            }
        }

        log.debug("Analyzed transactions = " + numberOfAnalyzedTransactions);
        if (tip == null) {
            numberOfConfirmedTransactions = numberOfAnalyzedTransactions;
        }
        log.debug("Confirmed transactions = " + numberOfConfirmedTransactions);
        return state;
    }

    /**
     * Descends through the tree of transactions, through trunk and branch, marking each as {mark} until it reaches
     * a transaction while the transaction snapshot marker is mutually exclusive to {mark}
     * @param hash start of the update tree
     * @param mark
     * @throws Exception
     */
    private static void updateSnapshotMilestone(Hash hash, boolean mark) throws Exception {
        Set<Hash> visitedHashes = new HashSet<>();
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(hash));
        Hash hashPointer;
        while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
            if (visitedHashes.add(hashPointer)) {
                final TransactionViewModel transactionViewModel2 = TransactionViewModel.fromHash(hashPointer);
                if(transactionViewModel2.hasSnapshot() ^ mark) {
                    transactionViewModel2.markSnapshot(mark);
                    nonAnalyzedTransactions.offer(transactionViewModel2.getTrunkTransactionHash());
                    nonAnalyzedTransactions.offer(transactionViewModel2.getBranchTransactionHash());
                }
            }
        }
    }

    /**
     * Descends through transactions, trunk and branch, beginning at {tip}, until it reaches a transaction marked as
     * snapshot, or until it reaches a transaction that has already been added to the transient consistent set.
     * @param tip
     * @throws Exception
     */
    private static void updateConsistentHashes(Hash tip) throws Exception {
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
        Hash hashPointer;
        while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
            final TransactionViewModel transactionViewModel2 = TransactionViewModel.fromHash(hashPointer);
            if(!transactionViewModel2.hasSnapshot() && approvedHashes.add(hashPointer)) {
                nonAnalyzedTransactions.offer(transactionViewModel2.getTrunkTransactionHash());
                nonAnalyzedTransactions.offer(transactionViewModel2.getBranchTransactionHash());
            }
        }
    }

    /**
     * Initializes the LedgerValidator. This updates the latest milestone and solid subtangle milestone, and then
     * builds up the snapshot until it reaches the latest consistent snapshot. If any inconsistencies are detected,
     * perhaps by database corruption, it will delete the milestone snapshot and all that follow.
     * It then starts at the earliest consistent milestone index with a snapshot, and analyzes the tangle until it
     * either reaches the latest solid subtangle milestone, or until it reaches an inconsistent milestone.
     * @throws Exception
     */
    protected static void init() throws Exception {
        int separator = 1;
        long start, duration;
        final long expected = 5000;
        Milestone.instance().updateLatestMilestone();
        log.info("Latest Milestone index: " + Milestone.latestMilestoneIndex);
        Milestone.updateLatestSolidSubtangleMilestone();
        log.info("Latest SOLID Milestone index:" + Milestone.latestSolidSubtangleMilestoneIndex);
        MilestoneViewModel latestConsistentMilestone = buildSnapshot();
        if(latestConsistentMilestone != null) {
            updateSnapshotMilestone(latestConsistentMilestone.getHash(), true);
        }
        int i = latestConsistentMilestone == null? Milestone.MILESTONE_START_INDEX: latestConsistentMilestone.index();
        while(i++ < Milestone.latestSolidSubtangleMilestoneIndex) {
            start = System.currentTimeMillis();
            if(!MilestoneViewModel.load(i)) {
                Map.Entry<Integer, Hash> closestGreaterMilestone = Milestone.findMilestone(i);
                new MilestoneViewModel(closestGreaterMilestone.getKey(), closestGreaterMilestone.getValue()).store();
            }
            if(updateSnapshot(MilestoneViewModel.get(i))) {
                log.info("Snapshot created at Milestone: " + i);
            } else {
                break;
            }
            duration = System.currentTimeMillis() - start;
            separator = getSeparator(duration, expected, separator, i, Milestone.latestSolidSubtangleMilestoneIndex);
            if(i < Milestone.latestSolidSubtangleMilestoneIndex - separator) {
                i += separator;
            }
        }
    }

    private static int getSeparator(long duration, long expected, int separator, int currentIndex, int max) {
        int difference = max - currentIndex;

        separator *= (double)(((double) expected) / ((double) duration));
        while(currentIndex > max - separator) {
            separator >>= 1;
        }
        return separator;
    }

    public static boolean isApproved(Hash hash) {
        return approvedHashes.contains(hash);
    }


    /**
     * Only called once upon initialization, this builds the {latestSnapshot} state up to the most recent
     * solid milestone snapshot. It gets the earliest snapshot, and while checking for consistency, patches the next
     * newest snapshot diff into its map.
     * @return              the most recent consistent milestone with a snapshot.
     * @throws Exception
     */
    private static MilestoneViewModel buildSnapshot() throws Exception {
        Snapshot updatedSnapshot = latestSnapshot;
        MilestoneViewModel consistentMilestone = null;
        MilestoneViewModel snapshotMilestone = MilestoneViewModel.firstWithSnapshot();
        while(snapshotMilestone != null) {
            updatedSnapshot = updatedSnapshot.patch(snapshotMilestone.snapshot());
            if(updatedSnapshot.isConsistent()) {
                consistentMilestone = snapshotMilestone;
                latestSnapshot.merge(updatedSnapshot);
                snapshotMilestone = snapshotMilestone.nextWithSnapshot();
            } else {
                while (snapshotMilestone != null) {
                    updateSnapshotMilestone(snapshotMilestone.getHash(), false);
                    snapshotMilestone.delete();
                    snapshotMilestone = snapshotMilestone.nextWithSnapshot();
                }
            }
        }
        return consistentMilestone;
    }

    public static boolean updateSnapshot(MilestoneViewModel milestone) throws Exception {
        TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(milestone.getHash());
        boolean isConsistent = transactionViewModel.hasSnapshot();
        if(!isConsistent) {
            Hash tail = transactionViewModel.getHash();
            Map<Hash, Long> currentState = getLatestDiff(tail, true);
            isConsistent = currentState != null && latestSnapshot.patch(currentState).isConsistent();
            if (isConsistent) {
                synchronized (updateSyncObject) {
                    updateSnapshotMilestone(milestone.getHash(), true);
                    approvedHashes.clear();
                    milestone.initSnapshot(currentState);
                    milestone.updateSnapshot();
                    latestSnapshot.merge(latestSnapshot.patch(milestone.snapshot()));
                    stateSinceMilestone.merge(latestSnapshot);
                }
            }
        }
        return isConsistent;
    }

    public static boolean updateFromSnapshot(Hash tip) throws Exception {
        TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tip);
        boolean isConsistent = approvedHashes.contains(tip);
        if(!isConsistent) {
            Hash tail = transactionViewModel.getHash();
            Map<Hash, Long> currentState = getLatestDiff(tail, false);
            isConsistent = currentState != null && stateSinceMilestone.patch(currentState).isConsistent();
            if (isConsistent) {
                synchronized (updateSyncObject) {
                    updateConsistentHashes(tip);
                    stateSinceMilestone.merge(stateSinceMilestone.patch(currentState));
                }
            }
        }
        return isConsistent;
    }
}
