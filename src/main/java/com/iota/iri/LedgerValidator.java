package com.iota.iri;

import com.iota.iri.controllers.*;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.zmq.MessageQ;
import com.iota.iri.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.iota.iri.Snapshot.latestSnapshotSyncObject;

/**
 * Created by paul on 4/15/17.
 */
public class LedgerValidator {

    private final Logger log = LoggerFactory.getLogger(LedgerValidator.class);
    private final Object approvalsSyncObject = new Object();
    private final Snapshot stateSinceMilestone;
    private final Snapshot latestSnapshot;
    private final Set<Hash> approvedHashes = new HashSet<>();
    private final Tangle tangle;
    private final Milestone milestone;
    private final TransactionRequester transactionRequester;
    private final MessageQ messageQ;
    private volatile int numberOfConfirmedTransactions;

    public LedgerValidator(Tangle tangle, final Snapshot latestSnapshot, Milestone milestone, TransactionRequester transactionRequester, MessageQ messageQ) {
        this.tangle = tangle;
        this.milestone = milestone;
        this.transactionRequester = transactionRequester;
        stateSinceMilestone = new Snapshot(latestSnapshot);
        this.latestSnapshot = latestSnapshot;
        this.messageQ = messageQ;
    }

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
     * until it reaches a transaction that is marked as a "confirmed" transaction.
     * If {milestone} is false, it will search up until it reaches a confirmed transaction, or until it finds a hash that has been
     * marked as consistent since the previous milestone.
     * @param tip       the hash of a transaction to start the search from
     * @param milestone marker to indicate whether to stop only at confirmed transactions
     * @return {state}  the addresses that have a balance changed since the last diff check
     * @throws Exception
     */
    private Map<Hash,Long> getLatestDiff(Hash tip, int latestSnapshotIndex, boolean milestone) throws Exception {
        Map<Hash, Long> state = new HashMap<>();
        int numberOfAnalyzedTransactions = 0;
        Set<Hash> analyzedTips = new HashSet<>(Collections.singleton(Hash.NULL_HASH));
        Set<Hash> countedTx = new HashSet<>(Collections.singleton(Hash.NULL_HASH));

        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
        Hash transactionPointer;
        boolean keepScanning;
        while ((transactionPointer = nonAnalyzedTransactions.poll()) != null) {
            if(milestone) {
                keepScanning = true;
            } else {
                synchronized (approvalsSyncObject) {
                    keepScanning = !approvedHashes.contains(transactionPointer);
                }
            }
            if (analyzedTips.add(transactionPointer) && keepScanning) {

                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, transactionPointer);
                if (transactionViewModel.snapshotIndex() == 0 || transactionViewModel.snapshotIndex() > latestSnapshotIndex) {
                    numberOfAnalyzedTransactions++;
                    if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
                        transactionRequester.requestTransaction(transactionViewModel.getHash(), milestone);
                        return null;

                    } else {

                        if (transactionViewModel.getCurrentIndex() == 0) {

                            boolean validBundle = false;

                            final List<List<TransactionViewModel>> bundleTransactions = BundleValidator.validate(tangle, transactionViewModel.getBundleHash());
                            /*
                            for(List<TransactionViewModel> transactions: bundleTransactions) {
                                if (transactions.size() > 0) {
                                    int index = transactions.get(0).snapshotIndex();
                                    if (index > 0 && index <= latestSnapshotIndex) {
                                        return null;
                                    }
                                }
                            }
                            */
                            for (final List<TransactionViewModel> bundleTransactionViewModels : bundleTransactions) {

                                if(BundleValidator.isInconsistent(bundleTransactionViewModels, milestone)) {
                                    break;
                                }
                                if (bundleTransactionViewModels.get(0).getHash().equals(transactionViewModel.getHash())) {

                                    validBundle = true;

                                    for (final TransactionViewModel bundleTransactionViewModel : bundleTransactionViewModels) {

                                        if (bundleTransactionViewModel.value() != 0 && countedTx.add(bundleTransactionViewModel.getHash())) {

                                            final Hash address = bundleTransactionViewModel.getAddressHash();
                                            final Long value = state.get(address);
                                            state.put(address, value == null ? bundleTransactionViewModel.value()
                                                    : (value + bundleTransactionViewModel.value()));
                                        }
                                    }

                                    break;
                                }
                            }
                            if (!validBundle) {
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
     * a transaction while the transaction confirmed marker is mutually exclusive to {mark}
     * // old @param hash start of the update tree
     * @param milestone milestone to traverse from
     * @throws Exception
     */
    private void updateSnapshotMilestone(MilestoneViewModel milestone) throws Exception {
        Set<Hash> visitedHashes = new HashSet<>();
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(milestone.getHash()));
        int index = milestone.index();
        Hash hashPointer;
        while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
            if (visitedHashes.add(hashPointer)) {
                final TransactionViewModel transactionViewModel2 = TransactionViewModel.fromHash(tangle, hashPointer);
                if(transactionViewModel2.snapshotIndex() == 0) {
                    transactionViewModel2.setSnapshot(tangle, index);
                    messageQ.publish("%s %s %d sn", transactionViewModel2.getAddressHash(), transactionViewModel2.getHash(), index);
                    messageQ.publish("sn %d %s %s %s %s %s", index, transactionViewModel2.getHash(),
                            transactionViewModel2.getAddressHash(),
                            transactionViewModel2.getTrunkTransactionHash(),
                            transactionViewModel2.getBranchTransactionHash(),
                            transactionViewModel2.getBundleHash());
                    nonAnalyzedTransactions.offer(transactionViewModel2.getTrunkTransactionHash());
                    nonAnalyzedTransactions.offer(transactionViewModel2.getBranchTransactionHash());
                }
            }
        }
    }

    /**
     * Descends through transactions, trunk and branch, beginning at {tip}, until it reaches a transaction marked as
     * confirmed, or until it reaches a transaction that has already been added to the transient consistent set.
     * @param tip
     * @throws Exception
     */
    private void updateConsistentHashes(Hash tip, int index) throws Exception {
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
        Hash hashPointer;
        boolean keepTraversing;
        while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
            final TransactionViewModel transactionViewModel2 = TransactionViewModel.fromHash(tangle, hashPointer);
            if((transactionViewModel2.snapshotIndex() == 0 || transactionViewModel2.snapshotIndex() > index) ) {
                synchronized (approvalsSyncObject) {
                    keepTraversing = approvedHashes.add(hashPointer);
                }
                if(keepTraversing) {
                    nonAnalyzedTransactions.offer(transactionViewModel2.getTrunkTransactionHash());
                    nonAnalyzedTransactions.offer(transactionViewModel2.getBranchTransactionHash());
                }
            }
        }
    }

    /**
     * Initializes the LedgerValidator. This updates the latest milestone and solid subtangle milestone, and then
     * builds up the confirmed until it reaches the latest consistent confirmed. If any inconsistencies are detected,
     * perhaps by database corruption, it will delete the milestone confirmed and all that follow.
     * It then starts at the earliest consistent milestone index with a confirmed, and analyzes the tangle until it
     * either reaches the latest solid subtangle milestone, or until it reaches an inconsistent milestone.
     * @throws Exception
     */
    protected void init() throws Exception {
        MilestoneViewModel latestConsistentMilestone = buildSnapshot();
        if(latestConsistentMilestone != null) {
            milestone.latestSolidSubtangleMilestone = latestConsistentMilestone.getHash();
            milestone.latestSolidSubtangleMilestoneIndex = latestConsistentMilestone.index();
        }
    }

    public boolean isApproved(Hash hash) {
        synchronized (approvalsSyncObject) {
            return approvedHashes.contains(hash);
        }
    }


    /**
     * Only called once upon initialization, this builds the {latestSnapshot} state up to the most recent
     * solid milestone confirmed. It gets the earliest confirmed, and while checking for consistency, patches the next
     * newest confirmed diff into its map.
     * @return              the most recent consistent milestone with a confirmed.
     * @throws Exception
     */
    private MilestoneViewModel buildSnapshot() throws Exception {
        MilestoneViewModel consistentMilestone = null;
        synchronized (latestSnapshotSyncObject) {
            Snapshot updatedSnapshot = latestSnapshot.patch(new HashMap<>(), 0);
            StateDiffViewModel stateDiffViewModel;
            MilestoneViewModel snapshotMilestone = MilestoneViewModel.firstWithSnapshot(tangle);
            while (snapshotMilestone != null) {
                stateDiffViewModel = StateDiffViewModel.load(tangle, snapshotMilestone.getHash());
                updatedSnapshot = updatedSnapshot.patch(stateDiffViewModel.getDiff(), snapshotMilestone.index());
                if (updatedSnapshot.isConsistent()) {
                    consistentMilestone = snapshotMilestone;
                    latestSnapshot.merge(updatedSnapshot);
                    snapshotMilestone = snapshotMilestone.nextWithSnapshot(tangle);
                }
            }
        }
        return consistentMilestone;
    }

    public boolean updateSnapshot(MilestoneViewModel milestone) throws Exception {
        TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, milestone.getHash());
        synchronized (latestSnapshotSyncObject) {
            final int lastSnapshotIndex = latestSnapshot.index();
            final int transactionSnapshotIndex = transactionViewModel.snapshotIndex();
            boolean hasSnapshot = transactionSnapshotIndex != 0;
            if(!hasSnapshot) {
                Hash tail = transactionViewModel.getHash();
                Map<Hash, Long> currentState = getLatestDiff(tail, lastSnapshotIndex, true);
                hasSnapshot = currentState != null && latestSnapshot.patch(currentState, milestone.index()).isConsistent();
                if (hasSnapshot) {
                    updateSnapshotMilestone(milestone);
                    synchronized (approvalsSyncObject) {
                        approvedHashes.clear();
                    }
                    StateDiffViewModel stateDiffViewModel;
                    stateDiffViewModel = new StateDiffViewModel(currentState, milestone.getHash());
                    if(currentState.size() != 0) {
                        stateDiffViewModel.store(tangle);
                    }
                    latestSnapshot.merge(latestSnapshot.patch(stateDiffViewModel.getDiff(), milestone.index()));
                }
            }
            if(stateSinceMilestone.index() < latestSnapshot.index()) {
                stateSinceMilestone.merge(latestSnapshot);
            }
            return hasSnapshot;
        }
    }

    public boolean updateFromSnapshot(Hash tip) throws Exception {
        TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, tip);
        boolean isConsistent;
        synchronized (latestSnapshotSyncObject) {
            synchronized (approvalsSyncObject) {
            isConsistent = approvedHashes.contains(tip);
            if (!isConsistent) {
                Hash tail = transactionViewModel.getHash();
                    int latestSyncIndex = latestSnapshot.index();
                    Map<Hash, Long> currentState = getLatestDiff(tail, latestSyncIndex, false);
                    isConsistent = currentState != null && stateSinceMilestone.patch(currentState, latestSyncIndex).isConsistent();
                    if (isConsistent) {
                        updateConsistentHashes(tip, latestSyncIndex);
                        stateSinceMilestone.merge(stateSinceMilestone.patch(currentState, latestSyncIndex));
                    }
                }
            }
        }
        return isConsistent;
    }
}
