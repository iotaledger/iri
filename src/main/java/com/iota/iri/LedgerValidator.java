package com.iota.iri;

import com.iota.iri.model.Hash;
import com.iota.iri.service.viewModels.MilestoneViewModel;
import com.iota.iri.service.viewModels.TransactionRequester;
import com.iota.iri.service.viewModels.TransactionViewModel;
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
    private static final Set<Hash> consistentHashes = new HashSet<>();
    private static volatile int numberOfConfirmedTransactions;

    private static Map<Hash,Long> getLatestDiff(Hash tip, boolean milestone) throws Exception {
        Map<Hash, Long> state = new HashMap<>();
        int numberOfAnalyzedTransactions = 0;
        Set<Hash> analyzedTips = new HashSet<>(Collections.singleton(Hash.NULL_HASH));
        Set<Hash> countedTx = new HashSet<>(Collections.singleton(Hash.NULL_HASH));

        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
        Hash transactionPointer;
        while ((transactionPointer = nonAnalyzedTransactions.poll()) != null) {

            if (analyzedTips.add(transactionPointer)) {

                numberOfAnalyzedTransactions++;

                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(transactionPointer);
                if(!transactionViewModel.hasSnapshot() && (milestone || !consistentHashes.contains(transactionPointer))) {
                    if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
                        TransactionRequester.instance().requestTransaction(transactionViewModel.getHash());
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
                                    TransactionRequester.instance().requestTransaction(transactionViewModel1.getHash());
                                }
                                return null;
                            }
                        }

                        nonAnalyzedTransactions.offer(transactionViewModel.getTrunkTransactionHash());
                        nonAnalyzedTransactions.offer(transactionViewModel.getBranchTransactionHash());
                    }
                } else {
                    log.debug("It is solid here");
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

    private static void updateSnapshotMilestone(Hash milestone, boolean mark) throws Exception {
        Set<Hash> visitedHashes = new HashSet<>();
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(milestone));
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
    private static void updateConsistentHashes(Hash tip) throws Exception {
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
        Hash hashPointer;
        while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
            final TransactionViewModel transactionViewModel2 = TransactionViewModel.fromHash(hashPointer);
            if(!transactionViewModel2.hasSnapshot() && consistentHashes.add(hashPointer)) {
                nonAnalyzedTransactions.offer(transactionViewModel2.getTrunkTransactionHash());
                nonAnalyzedTransactions.offer(transactionViewModel2.getBranchTransactionHash());
            }
        }
    }
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
        stateSinceMilestone.merge(latestSnapshot);
    }

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

    private static int getSeparator(long duration, long expected, int separator, int currentIndex, int max) {
        separator *= (double)(((double) expected) / ((double) duration));
        while(currentIndex > max - separator) {
            separator >>= 1;
        }
        return separator;
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
                    consistentHashes.clear();
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
        boolean isConsistent = consistentHashes.contains(tip);
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
