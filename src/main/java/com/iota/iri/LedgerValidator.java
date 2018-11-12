package com.iota.iri;

import com.iota.iri.controllers.*;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.zmq.MessageQ;
import com.iota.iri.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class LedgerValidator {

    private final Logger log = LoggerFactory.getLogger(LedgerValidator.class);
    private final Tangle tangle;
    private final SnapshotProvider snapshotProvider;
    private final MilestoneTracker milestoneTracker;
    private final TransactionRequester transactionRequester;
    private final MessageQ messageQ;
    private volatile int numberOfConfirmedTransactions;

    public LedgerValidator(Tangle tangle, SnapshotProvider snapshotProvider, MilestoneTracker milestoneTracker, TransactionRequester transactionRequester, MessageQ messageQ) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.milestoneTracker = milestoneTracker;
        this.transactionRequester = transactionRequester;
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
     * @param visitedNonMilestoneSubtangleHashes hashes that have been visited and considered as approved
     * @param tip                                the hash of a transaction to start the search from
     * @param latestSnapshotIndex                index of the latest snapshot to traverse to
     * @param milestone                          marker to indicate whether to stop only at confirmed transactions
     * @return {state}                           the addresses that have a balance changed since the last diff check
     * @throws Exception
     */
    public Map<Hash,Long> getLatestDiff(final Set<Hash> visitedNonMilestoneSubtangleHashes, Hash tip, int latestSnapshotIndex, boolean milestone) throws Exception {
        Map<Hash, Long> state = new HashMap<>();
        int numberOfAnalyzedTransactions = 0;
        Set<Hash> countedTx = new HashSet<>(Collections.singleton(Hash.NULL_HASH));

        visitedNonMilestoneSubtangleHashes.add(Hash.NULL_HASH);

        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
        Hash transactionPointer;
        while ((transactionPointer = nonAnalyzedTransactions.poll()) != null) {
            if (visitedNonMilestoneSubtangleHashes.add(transactionPointer)) {

                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, transactionPointer);
                if (transactionViewModel.snapshotIndex() == 0 || transactionViewModel.snapshotIndex() > latestSnapshotIndex) {
                    numberOfAnalyzedTransactions++;
                    if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
                        transactionRequester.requestTransaction(transactionViewModel.getHash(), milestone);
                        return null;

                    } else {

                        if (transactionViewModel.getCurrentIndex() == 0) {

                            boolean validBundle = false;

                            final List<List<TransactionViewModel>> bundleTransactions = BundleValidator.validate(tangle, snapshotProvider.getInitialSnapshot(), transactionViewModel.getHash());
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

                                if(BundleValidator.isInconsistent(bundleTransactionViewModels)) {
                                    break;
                                }
                                if (bundleTransactionViewModels.get(0).getHash().equals(transactionViewModel.getHash())) {

                                    validBundle = true;

                                    for (final TransactionViewModel bundleTransactionViewModel : bundleTransactionViewModels) {

                                        if (bundleTransactionViewModel.value() != 0 && countedTx.add(bundleTransactionViewModel.getHash())) {

                                            final Hash address = bundleTransactionViewModel.getAddressHash();
                                            final Long value = state.get(address);
                                            state.put(address, value == null ? bundleTransactionViewModel.value()
                                                    : Math.addExact(value, bundleTransactionViewModel.value()));
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
     * @param hash tail to traverse from
     * @param index milestone index
     * @throws Exception
     */
    private void updateSnapshotMilestone(Hash hash, int index) throws Exception {
        Set<Hash> visitedHashes = new HashSet<>();
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(hash));
        Hash hashPointer;
        while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
            if (visitedHashes.add(hashPointer)) {
                final TransactionViewModel transactionViewModel2 = TransactionViewModel.fromHash(tangle, hashPointer);
                if(transactionViewModel2.snapshotIndex() == 0) {
                    transactionViewModel2.setSnapshot(tangle, snapshotProvider.getInitialSnapshot(), index);
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
    private void updateConsistentHashes(final Set<Hash> visitedHashes, Hash tip, int index) throws Exception {
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
        Hash hashPointer;
        while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
            final TransactionViewModel transactionViewModel2 = TransactionViewModel.fromHash(tangle, hashPointer);
            if((transactionViewModel2.snapshotIndex() == 0 || transactionViewModel2.snapshotIndex() > index) ) {
                if(visitedHashes.add(hashPointer)) {
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
            log.info("Loaded consistent milestone: #" + latestConsistentMilestone.index());

            milestoneTracker.latestSolidSubtangleMilestone = latestConsistentMilestone.getHash();
            milestoneTracker.latestSolidSubtangleMilestoneIndex = latestConsistentMilestone.index();
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
        milestoneTracker.latestSnapshot.rwlock.writeLock().lock();
        try {
            MilestoneViewModel candidateMilestone = MilestoneViewModel.first(tangle);
            while (candidateMilestone != null) {
                if (candidateMilestone.index() % 10000 == 0) {
                    StringBuilder logMessage = new StringBuilder();

                    logMessage.append("Building snapshot... Consistent: #");
                    logMessage.append(consistentMilestone != null ? consistentMilestone.index() : -1);
                    logMessage.append(", Candidate: #");
                    logMessage.append(candidateMilestone.index());

                    log.info(logMessage.toString());
                }
                if (StateDiffViewModel.maybeExists(tangle, candidateMilestone.getHash())) {
                    StateDiffViewModel stateDiffViewModel = StateDiffViewModel.load(tangle, candidateMilestone.getHash());

                    if (stateDiffViewModel != null && !stateDiffViewModel.isEmpty()) {
                        if (Snapshot.isConsistent(milestoneTracker.latestSnapshot.patchedDiff(stateDiffViewModel.getDiff()))) {
                            milestoneTracker.latestSnapshot.apply(stateDiffViewModel.getDiff(), candidateMilestone.index());
                            consistentMilestone = candidateMilestone;
                        } else {
                            break;
                        }
                    }
                }
                candidateMilestone = candidateMilestone.next(tangle);
            }
        } finally {
            milestoneTracker.latestSnapshot.rwlock.writeLock().unlock();
        }
        return consistentMilestone;
    }

    public boolean updateSnapshot(MilestoneViewModel milestoneVM) throws Exception {
        TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, milestoneVM.getHash());
        milestoneTracker.latestSnapshot.rwlock.writeLock().lock();
        try {
            final int transactionSnapshotIndex = transactionViewModel.snapshotIndex();
            boolean hasSnapshot = transactionSnapshotIndex != 0;
            if (!hasSnapshot) {
                Hash tail = transactionViewModel.getHash();
                Map<Hash, Long> currentState = getLatestDiff(new HashSet<>(), tail, milestoneTracker.latestSnapshot.index(), true);
                hasSnapshot = currentState != null && Snapshot.isConsistent(milestoneTracker.latestSnapshot.patchedDiff(currentState));
                if (hasSnapshot) {
                    updateSnapshotMilestone(milestoneVM.getHash(), milestoneVM.index());
                    StateDiffViewModel stateDiffViewModel;
                    stateDiffViewModel = new StateDiffViewModel(currentState, milestoneVM.getHash());
                    if (currentState.size() != 0) {
                        stateDiffViewModel.store(tangle);
                    }
                    milestoneTracker.latestSnapshot.apply(currentState, milestoneVM.index());
                }
            }
            return hasSnapshot;
        } finally {
            milestoneTracker.latestSnapshot.rwlock.writeLock().unlock();
        }
    }

    public boolean checkConsistency(List<Hash> hashes) throws Exception {
        Set<Hash> visitedHashes = new HashSet<>();
        Map<Hash, Long> diff = new HashMap<>();
        for (Hash hash : hashes) {
            if (!updateDiff(visitedHashes, diff, hash)) {
                return false;
            }
        }
        return true;
    }

    public boolean updateDiff(Set<Hash> approvedHashes, final Map<Hash, Long> diff, Hash tip) throws Exception {
        if(!TransactionViewModel.fromHash(tangle, tip).isSolid()) {
            return false;
        }
        if (approvedHashes.contains(tip)) {
            return true;
        }
        Set<Hash> visitedHashes = new HashSet<>(approvedHashes);
        Map<Hash, Long> currentState = getLatestDiff(visitedHashes, tip, milestoneTracker.latestSnapshot.index(), false);
        if (currentState == null) {
            return false;
        }
        diff.forEach((key, value) -> {
            if(currentState.computeIfPresent(key, ((hash, aLong) -> value + aLong)) == null) {
                currentState.putIfAbsent(key, value);
            }
        });
        boolean isConsistent = Snapshot.isConsistent(milestoneTracker.latestSnapshot.patchedDiff(currentState));
        if (isConsistent) {
            diff.putAll(currentState);
            approvedHashes.addAll(visitedHashes);
        }
        return isConsistent;
    }
}
