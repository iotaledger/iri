package com.iota.iri.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Bundle;
import com.iota.iri.Milestone;
import com.iota.iri.Snapshot;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.service.storage.Storage;
import com.iota.iri.service.storage.StorageApprovers;
import com.iota.iri.service.storage.StorageTransactions;
import com.iota.iri.utils.Converter;

public class TipsManager {

    private static final Logger log = LoggerFactory.getLogger(TipsManager.class);

    private volatile boolean shuttingDown;
    
    private static int strategyMAXBars = 1;
    public static void setStrategyMAXBars(int strategyMAXBars) {
        TipsManager.strategyMAXBars = strategyMAXBars;
    }

    private static int strategyRSQBars = 2;
    public static void setStrategyRSQBars(int strategyRSQBars) {
        TipsManager.strategyRSQBars = strategyRSQBars;
    }

    private static int strategyBarCount = 0;
    private static final int strategyMAX = 0;
    private static final int strategyRSQ = 1;

    public void init() {

        (new Thread(() -> {

            while (!shuttingDown) {

                try {
                    final int previousLatestMilestoneIndex = Milestone.latestMilestoneIndex;
                    final int previousSolidSubtangleLatestMilestoneIndex = Milestone.latestSolidSubtangleMilestoneIndex;

                    Milestone.updateLatestMilestone();
                    Milestone.updateLatestSolidSubtangleMilestone();

                    if (previousLatestMilestoneIndex != Milestone.latestMilestoneIndex) {
                        log.info("Latest milestone has changed from #" + previousLatestMilestoneIndex + " to #"
                                + Milestone.latestMilestoneIndex);
                    }
                    if (previousSolidSubtangleLatestMilestoneIndex != Milestone.latestSolidSubtangleMilestoneIndex) {
                        log.info("Latest SOLID SUBTANGLE milestone has changed from #"
                                + previousSolidSubtangleLatestMilestoneIndex + " to #"
                                + Milestone.latestSolidSubtangleMilestoneIndex);
                    }
                    Thread.sleep(5000);

                } catch (final Exception e) {
                    log.error("Error during TipsManager Milestone updating", e);
                }
            }
        }, "Latest Milestone Tracker")).start();
    }

    public void shutDown() {
        shuttingDown = true;
    }

    static synchronized Hash transactionToApprove(final Hash extraTip, final int depth) {

        final Hash preferableMilestone = Milestone.latestSolidSubtangleMilestone;

        final Set<Long> analyzedTransactions_1 = new HashSet<>();
        final Set<Long> analyzedTransactions_2 = new HashSet<>();
        
        int currentStrategy;
        String currentStrategyName;

        Map<Hash, Long> state = new HashMap<>(Snapshot.initialState);

        if (extraTip == null) {
            strategyBarCount++;
        }
        if (strategyBarCount > (strategyMAXBars + strategyRSQBars) ) {
            strategyBarCount = 1;
        }
        if (strategyBarCount <= strategyMAXBars) {
            currentStrategy = strategyMAX;
            currentStrategyName = "MAX";
        }
        else {
            currentStrategy = strategyRSQ;
            currentStrategyName = "RSQ";
        }
        
        {
            int numberOfAnalyzedTransactions = 0;

            final Queue<Long> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(StorageTransactions
                    .instance().transactionPointer((extraTip == null ? preferableMilestone : extraTip).bytes())));
            Long pointer;
            while ((pointer = nonAnalyzedTransactions.poll()) != null) {

                if (analyzedTransactions_1.add(pointer)) {

                    numberOfAnalyzedTransactions++;

                    final Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);
                    if (transaction.type == Storage.PREFILLED_SLOT) {
                        log.info("lastestSolidMilestone = {}",Milestone.latestSolidSubtangleMilestoneIndex);
                        return preferableMilestone;
                    } else {

                        if (transaction.currentIndex == 0) {

                            boolean validBundle = false;

                            final Bundle bundle = new Bundle(transaction.bundle);
                            for (final List<Transaction> bundleTransactions : bundle.getTransactions()) {

                                if (bundleTransactions.get(0).pointer == transaction.pointer) {

                                    validBundle = true;

                                    bundleTransactions.stream()
                                            .filter(bundleTransaction -> bundleTransaction.value != 0)
                                            .forEach(bundleTransaction -> {
                                                final Hash address = new Hash(bundleTransaction.address);
                                                final Long value = state.get(address);
                                                state.put(address, value == null ? bundleTransaction.value
                                                        : (value + bundleTransaction.value));
                                            });
                                    break;
                                }
                            }

                            if (!validBundle) {
                                log.info("Bundle not valid");
                                return null;
                            }
                        }                     

                        nonAnalyzedTransactions.offer(transaction.trunkTransactionPointer);
                        nonAnalyzedTransactions.offer(transaction.branchTransactionPointer);

                    }
                }
            }

            log.info("Analyzed transactions = {}", numberOfAnalyzedTransactions);
        }

        final Iterator<Map.Entry<Hash, Long>> stateIterator = state.entrySet().iterator();
        while (stateIterator.hasNext()) {

            final Map.Entry<Hash, Long> entry = stateIterator.next();
            if (entry.getValue() <= 0) {

                if (entry.getValue() < 0) {
                    log.error("Ledger inconsistency detected");                    
                    return null;
                }
                stateIterator.remove();
            }
        }

        final Set<Hash> tailsToAnalyze = new HashSet<>();

        Hash tip = preferableMilestone;
        if (extraTip != null) {
            // Start at milestone at depth
            int searchDepth = depth;
            Hash deepHash = null;
            while ( searchDepth-- > 0 ) {
                deepHash = Milestone.getMilestone(Milestone.latestMilestoneIndex - searchDepth);
                if ( deepHash != null ) break;
            }
            if (deepHash != null) {
                StringBuffer sb = new StringBuffer();
                sb.append("search depth ");
                sb.append((searchDepth+1));
                sb.append(", strategy is ");
                sb.append(currentStrategyName);
                log.info(sb.toString());
                tip = deepHash;
            }            
        }
        final Queue<Long> nonAnalyzedTransactions = new LinkedList<>(
                Collections.singleton(StorageTransactions.instance().transactionPointer(tip.bytes())));
        
        Long pointer;
        while ((pointer = nonAnalyzedTransactions.poll()) != null) {    
            if (analyzedTransactions_2.add(pointer)) {
                final Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);
                if (transaction.currentIndex == 0) {
                    tailsToAnalyze.add(new Hash(transaction.hash, 0, Transaction.HASH_SIZE));
                }
                StorageApprovers.instance()
                        .approveeTransactions(StorageApprovers.instance().approveePointer(transaction.hash))
                        .forEach(nonAnalyzedTransactions::offer);
            }
        }

        if (extraTip != null) {
            final Iterator<Hash> tailsToAnalyzeIterator = tailsToAnalyze.iterator();
            while (tailsToAnalyzeIterator.hasNext()) {
                final Transaction tail = StorageTransactions.instance().loadTransaction(tailsToAnalyzeIterator.next().bytes());
                if (analyzedTransactions_1.contains(tail.pointer)) {
                    tailsToAnalyzeIterator.remove();
                }
            }
        }

        log.info(tailsToAnalyze.size() + " tails need to be analyzed");
        final Map<Hash, Long> tailsRatings = new HashMap<>();
        long totalRating = 0L;
        int bestRating = 0;
        Hash bestTip = preferableMilestone;
        for (final Hash tail : tailsToAnalyze) {

            Set<Hash> extraTransactions = new HashSet<>();

            // Avoid tips that have a branch depth larger than depth          
            long tailPointer = StorageTransactions.instance().transactionPointer(tail.bytes());
            Transaction tailTx = StorageTransactions.instance().loadTransaction(tailPointer);
            long branchPointer = tailTx.branchTransactionPointer;
            Transaction branchTx = StorageTransactions.instance().loadTransaction(branchPointer);
            int criticalDepth = depth;
            if (extraTip == null) criticalDepth = 2;
            if ( getDepth(branchTx.hash) > criticalDepth ) continue;

            nonAnalyzedTransactions.clear();
            nonAnalyzedTransactions.offer(tailPointer);
            while ((pointer = nonAnalyzedTransactions.poll()) != null) {

                if (analyzedTransactions_1.add(pointer)) {

                    final Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);
                    if (transaction.type == Storage.PREFILLED_SLOT) {
                        extraTransactions = null;
                        break;
                    } else {
                        extraTransactions.add(new Hash(transaction.hash, 0, Transaction.HASH_SIZE));                   
                        nonAnalyzedTransactions.offer(transaction.trunkTransactionPointer);
                        nonAnalyzedTransactions.offer(transaction.branchTransactionPointer);
                    }
                }
            }

            if (extraTransactions != null) {

                Set<Hash> extraTransactionsCopy = new HashSet<>(extraTransactions);

                for (final Hash extraTransaction : extraTransactions) {

                    final Transaction transaction = StorageTransactions.instance()
                            .loadTransaction(extraTransaction.bytes());
                    if (transaction != null && transaction.currentIndex == 0) {

                        final Bundle bundle = new Bundle(transaction.bundle);
                        for (final List<Transaction> bundleTransactions : bundle.getTransactions()) {

                            if (Arrays.equals(bundleTransactions.get(0).hash, transaction.hash)) {

                                for (final Transaction bundleTransaction : bundleTransactions) {

                                    if (!extraTransactionsCopy
                                            .remove(new Hash(bundleTransaction.hash, 0, Transaction.HASH_SIZE))) {
                                        extraTransactionsCopy = null;
                                        break;
                                    }
                                }
                                break;
                            }
                        }
                    }
                    if (extraTransactionsCopy == null) {
                        break;
                    }
                }

                if (extraTransactionsCopy != null && extraTransactionsCopy.isEmpty()) {

                    final Map<Hash, Long> stateCopy = new HashMap<>(state);

                    for (final Hash extraTransaction : extraTransactions) {

                        final Transaction transaction = StorageTransactions.instance()
                                .loadTransaction(extraTransaction.bytes());
                        if (transaction.value != 0) {
                            final Hash address = new Hash(transaction.address);
                            final Long value = stateCopy.get(address);
                            stateCopy.put(address, value == null ? transaction.value : (value + transaction.value));
                        }
                    }

                    for (final long value : stateCopy.values()) {
                        if (value < 0) {
                            extraTransactions = null;
                            break;
                        }
                    }

                    if (extraTransactions != null) {
                        if (currentStrategy == strategyMAX) {
                            if (extraTransactions.size() > bestRating) {
                                bestTip = tail;
                                bestRating = extraTransactions.size();
                            }
                        } else {
                            long extraTransactionSizeSquared = (long) (extraTransactions.size()) * ((long) extraTransactions.size());
                            tailsRatings.put(tail, extraTransactionSizeSquared);
                            totalRating += extraTransactionSizeSquared;
                        }
                    }
                }
            }
        }
        
        if (currentStrategy == strategyMAX) {
            log.info("{} extra transactions approved", bestRating);
            return bestTip;
        }
        
        if (totalRating > 0L) {
            long hit = ThreadLocalRandom.current().nextLong(totalRating);
            if (hit > 0L) {
                for (final Map.Entry<Hash, Long> entry : tailsRatings.entrySet()) {

                    if ((hit -= entry.getValue()) < 0L) {                     
                        log.info("{} extra transactions approved", (int)Math.sqrt(entry.getValue()));
                        return entry.getKey();
                    }
                }
            }
        }
        // If nothing selected, fall back to latest solid milestone
        return preferableMilestone;
    }

    private static int getDepth(byte[] hash) {
        final Queue<Long> depthQueue = new LinkedList<Long>(Collections.singleton(StorageTransactions.instance().transactionPointer(hash)));
        Long pointer;
        while((pointer = depthQueue.poll()) != null && pointer > 0L) {
            final Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);
            if (transaction.type == Storage.PREFILLED_SLOT)
                continue;
            if(Arrays.equals(transaction.address, Milestone.COORDINATOR.bytes())) {
                int[] trits = new int[Transaction.TAG_SIZE];
                Converter.getTrits(transaction.tag, trits);
                return (Milestone.latestMilestoneIndex - (int) Converter.longValue(trits, 0, Transaction.TAG_SIZE));             
            }
            depthQueue.offer(transaction.trunkTransactionPointer);
            depthQueue.offer(transaction.branchTransactionPointer);
        }
        return Integer.MAX_VALUE;
    }

    private static TipsManager instance = new TipsManager();

    private TipsManager() {
    }

    public static TipsManager instance() {
        return instance;
    }
}