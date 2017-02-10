package com.iota.iri.service;

import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Bundle;
import com.iota.iri.Milestone;
import com.iota.iri.Snapshot;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.service.storage.Storage;
import com.iota.iri.service.storage.StorageAddresses;
import com.iota.iri.service.storage.StorageApprovers;
import com.iota.iri.service.storage.StorageTransactions;
import com.iota.iri.utils.Converter;

public class TipsManager {

    private static final Logger log = LoggerFactory.getLogger(TipsManager.class);

    private static int RATING_THRESHOLD = 50; // Must be in [0..100] range
    
    private static int ARTIFICAL_LATENCY = 1200; 

    static boolean shuttingDown;

    static int numberOfConfirmedTransactions;

    static final byte[] analyzedTransactionsFlags = new byte[134217728];
    static final byte[] analyzedTransactionsFlagsCopy = new byte[134217728];
    static final byte[] zeroedAnalyzedTransactionsFlags = new byte[134217728];
    
    public static void setRATING_THRESHOLD(int value) {
        if (value < 0) value = 0;
        if (value > 100) value = 100;
        RATING_THRESHOLD = value;
    }
    
    public static void setARTIFICAL_LATENCY(int value) {
        ARTIFICAL_LATENCY = value;
    }
    
    public void init() {

        (new Thread(() -> {
            
            final SecureRandom rnd = new SecureRandom();

            while (!shuttingDown) {
                
                try {
                    final int previousLatestMilestoneIndex = Milestone.latestMilestoneIndex;
                    final int previousSolidSubtangleLatestMilestoneIndex = Milestone.latestSolidSubtangleMilestoneIndex;

                    Milestone.updateLatestMilestone();
                    Milestone.updateLatestSolidSubtangleMilestone();

                    if (previousLatestMilestoneIndex != Milestone.latestMilestoneIndex) {

                        log.info("Latest milestone has changed from #" + previousLatestMilestoneIndex
                                + " to #" + Milestone.latestMilestoneIndex);
                    }
                    if (previousSolidSubtangleLatestMilestoneIndex != Milestone.latestSolidSubtangleMilestoneIndex) {

                        log.info("Latest SOLID SUBTANGLE milestone has changed from #"
                                + previousSolidSubtangleLatestMilestoneIndex + " to #"
                                + Milestone.latestSolidSubtangleMilestoneIndex);
                    }

                    long latency = (long)((long)(rnd.nextInt(ARTIFICAL_LATENCY))*1000L)+5000L;
                    log.info("Next milestone check in {} seconds",latency/1000L);
                    Thread.sleep(latency);
                    
                } catch (final Exception e) {
                    log.error("Error during TipsManager Milestone updating", e);
                }
            }
        }, "Latest Milestone Tracker")).start();
    }

    static Hash transactionToApprove(final Hash extraTip, int depth) {

        final Hash preferableMilestone = Milestone.latestSolidSubtangleMilestone;
        
        final int oldestAcceptableMilestoneIndex = Milestone.latestSolidSubtangleMilestoneIndex - depth;
        
        long criticalArrivalTime = Long.MAX_VALUE;
        for (final Long pointer : StorageAddresses.instance().addressesOf(Milestone.COORDINATOR)) {
            final Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);
            if (transaction.currentIndex == 0) {
                int milestoneIndex = (int) Converter.longValue(transaction.trits(), Transaction.TAG_TRINARY_OFFSET, 15);
                if (milestoneIndex >= oldestAcceptableMilestoneIndex) {
                    long itsArrivalTime = 1000L * transaction.arrivalTime;
                    final long timestamp = (int) Converter.longValue(transaction.trits(), Transaction.TIMESTAMP_TRINARY_OFFSET, 27);
                    if (itsArrivalTime == 0) itsArrivalTime = 1000*timestamp;
                    if (itsArrivalTime < criticalArrivalTime) {                        
                        criticalArrivalTime = itsArrivalTime;
                    }
                }
            }
        }
        
        //DateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        //Calendar calendar = Calendar.getInstance();
        //calendar.setTimeInMillis(criticalArrivalTime);
        //log.info("Oldest accepted solid milestone index "+oldestAcceptableMilestoneIndex+", arrival time "+formatter.format(calendar.getTime()));
        
        System.arraycopy(zeroedAnalyzedTransactionsFlags, 0, analyzedTransactionsFlags, 0, 134217728);

        Map<Hash, Long> state = new HashMap<>(Snapshot.initialState);

        {
            int numberOfAnalyzedTransactions = 0;

            final Queue<Long> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(StorageTransactions
                    .instance().transactionPointer((extraTip == null ? preferableMilestone : extraTip).bytes())));
            Long pointer;
            while ((pointer = nonAnalyzedTransactions.poll()) != null) {

                if (setAnalyzedTransactionFlag(pointer)) {

                    numberOfAnalyzedTransactions++;

                    final Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);
                    if (transaction.type == Storage.PREFILLED_SLOT) {

                        return null;

                    } else {

                        if (transaction.currentIndex == 0) {

                            boolean validBundle = false;

                            final Bundle bundle = new Bundle(transaction.bundle);
                            for (final List<Transaction> bundleTransactions : bundle.getTransactions()) {

                                if (bundleTransactions.get(0).pointer == transaction.pointer) {

                                    validBundle = true;

                                    for (final Transaction bundleTransaction : bundleTransactions) {

                                        if (bundleTransaction.value != 0) {

                                            final Hash address = new Hash(bundleTransaction.address);
                                            final Long value = state.get(address);
                                            state.put(address, value == null ? bundleTransaction.value
                                                    : (value + bundleTransaction.value));
                                        }
                                    }

                                    break;
                                }
                            }

                            if (!validBundle) {

                                return null;
                            }
                        }

                        nonAnalyzedTransactions.offer(transaction.trunkTransactionPointer);
                        nonAnalyzedTransactions.offer(transaction.branchTransactionPointer);
                    }
                }
            }

            log.info( "Confirmed transactions = " + numberOfAnalyzedTransactions );
            if (extraTip == null) {
                numberOfConfirmedTransactions = numberOfAnalyzedTransactions;
            }
        }

        final Iterator<Map.Entry<Hash, Long>> stateIterator = state.entrySet().iterator();
        while (stateIterator.hasNext()) {

            final Map.Entry<Hash, Long> entry = stateIterator.next();
            if (entry.getValue() <= 0) {

                if (entry.getValue() < 0) {
                    log.info("Ledger inconsistency detected");
                    return null;
                }

                stateIterator.remove();
            }
            //////////// --Coo only--
            /*
             * if (entry.getValue() > 0) {
             * 
             * System.out.ln("initialState.put(new Hash(\"" +
             * entry.getKey() + "\"), " + entry.getValue() + "L);"); }
             */
            ////////////
        }

        System.arraycopy(analyzedTransactionsFlags, 0, analyzedTransactionsFlagsCopy, 0, 134217728);
        System.arraycopy(zeroedAnalyzedTransactionsFlags, 0, analyzedTransactionsFlags, 0, 134217728);

        final List<Long> tailsToAnalyze = new LinkedList<>();

        long tip = StorageTransactions.instance().transactionPointer(preferableMilestone.bytes());
        if (extraTip != null) {

            Transaction transaction = StorageTransactions.instance().loadTransaction(tip);
            while (depth-- > 0 && tip != Storage.CELLS_OFFSET - Storage.SUPER_GROUPS_OFFSET) {

                tip = transaction.pointer;
                do {

                    transaction = StorageTransactions.instance().loadTransaction(transaction.trunkTransactionPointer);

                } while (transaction.currentIndex != 0);
            }
        }
        final Queue<Long> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
        Long pointer;
        final Set<Long> tailsWithoutApprovers = new HashSet<>();
        while ((pointer = nonAnalyzedTransactions.poll()) != null) {

            if (setAnalyzedTransactionFlag(pointer)) {

                final Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);

                if (transaction.currentIndex == 0 && !tailsToAnalyze.contains(transaction.pointer)) {

                    tailsToAnalyze.add(transaction.pointer);
                }

                final long approveePointer = StorageApprovers.instance().approveePointer(transaction.hash);
                if (approveePointer == 0) {

                    if (transaction.currentIndex == 0) {

                        tailsWithoutApprovers.add(pointer);
                    }

                } else {

                    for (final Long approverPointer : StorageApprovers.instance()
                            .approveeTransactions(approveePointer)) {

                        nonAnalyzedTransactions.offer(approverPointer);
                    }
                }
            }
        }
        tailsToAnalyze.removeAll(tailsWithoutApprovers); // Remove them from
                                                         // where they are...
        tailsToAnalyze.addAll(tailsWithoutApprovers); // ...and add to the very
                                                      // end

        if (extraTip != null) {

            System.arraycopy(analyzedTransactionsFlagsCopy, 0, analyzedTransactionsFlags, 0, 134217728);

            final Iterator<Long> tailsToAnalyzeIterator = tailsToAnalyze.iterator();
            while (tailsToAnalyzeIterator.hasNext()) {

                final Long tailPointer = tailsToAnalyzeIterator.next();
                if ((analyzedTransactionsFlags[(int) ((tailPointer
                        - (Storage.CELLS_OFFSET - Storage.SUPER_GROUPS_OFFSET)) >> (11 + 3))]
                        & (1 << (((tailPointer - (Storage.CELLS_OFFSET - Storage.SUPER_GROUPS_OFFSET)) >> 11)
                                & 7))) != 0) {

                    tailsToAnalyzeIterator.remove();
                }
            }
        }

        log.info(tailsToAnalyze.size() + " tails need to be analyzed");

        /* --Coo only-- Hash bestTip = preferableMilestone; */
        int bestRating = 0;
        // final Set<Long> seenTails = new HashSet<>();

        /**/final Map<Hash, Integer> tailsRaitings = new HashMap<>();

        for (int i = tailsToAnalyze.size(); i-- > 0;) {

            final Long tailPointer = tailsToAnalyze.get(i);
            /* -- Coo only--
             * if (seenTails.contains(tailPointer)) {
             * 
             * continue; }
             */

            System.arraycopy(analyzedTransactionsFlagsCopy, 0, analyzedTransactionsFlags, 0, 134217728);

            final Set<Long> extraTransactions = new HashSet<>();

            nonAnalyzedTransactions.clear();
            nonAnalyzedTransactions.offer(tailPointer);
            while ((pointer = nonAnalyzedTransactions.poll()) != null) {

                if (setAnalyzedTransactionFlag(pointer)) {

                    final Transaction transaction = StorageTransactions.instance().loadTransaction(pointer);
                    if (transaction.type == Storage.PREFILLED_SLOT) {

                        // -- Coo only-- seenTails.addAll(extraTransactions);

                        extraTransactions.clear();

                        break;

                    } else {

                        extraTransactions.add(pointer);

                        nonAnalyzedTransactions.offer(transaction.trunkTransactionPointer);
                        nonAnalyzedTransactions.offer(transaction.branchTransactionPointer);
                    }
                }
            }

            if (extraTransactions.size() > /* bestRating */0) {

                Set<Long> extraTransactionsCopy = new HashSet<>(extraTransactions);

                for (final Long extraTransactionPointer : extraTransactions) {

                    final Transaction transaction = StorageTransactions.instance()
                            .loadTransaction(extraTransactionPointer);
                    if (transaction.currentIndex == 0) {

                        final Bundle bundle = new Bundle(transaction.bundle);
                        for (final List<Transaction> bundleTransactions : bundle.getTransactions()) {

                            if (bundleTransactions.get(0).pointer == transaction.pointer) {

                                for (final Transaction bundleTransaction : bundleTransactions) {

                                    final long timestamp = (int) Converter.longValue(bundleTransaction.trits(), Transaction.TIMESTAMP_TRINARY_OFFSET, 27);
                                    long itsArrivalTime = bundleTransaction.arrivalTime;
                                    if (itsArrivalTime == 0) itsArrivalTime = 1000*timestamp;
                                                                        
                                    if ( itsArrivalTime < criticalArrivalTime ) {
                                        //formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                                        //calendar = Calendar.getInstance();
                                        //calendar.setTimeInMillis(criticalArrivalTime);
                                        //log.info("---discard this tail with arrival time "+formatter.format(calendar.getTime()));
                                        extraTransactionsCopy = null;
                                        break;
                                    }
                                    
                                    if (!extraTransactionsCopy.remove(bundleTransaction.pointer)) {
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

                    for (final Long extraTransactionPointer : extraTransactions) {

                        final Transaction transaction = StorageTransactions.instance()
                                .loadTransaction(extraTransactionPointer);
                        if (transaction.value != 0) {

                            final Hash address = new Hash(transaction.address);
                            final Long value = stateCopy.get(address);
                            stateCopy.put(address, value == null ? transaction.value : (value + transaction.value));
                        }
                    }

                    for (final long value : stateCopy.values()) {

                        if (value < 0) {

                            extraTransactions.clear();

                            break;
                        }
                    }

                    if (!extraTransactions.isEmpty()) {

                        // --Coo only--
                        // bestTip = new
                        // Hash(Storage.loadTransaction(tailPointer).hash, 0,
                        // Transaction.HASH_SIZE);
                        // bestRating = extraTransactions.size();
                        // seenTails.addAll(extraTransactions);

                        /**/tailsRaitings.put(new Hash(StorageTransactions.instance().loadTransaction(tailPointer).hash,
                                0, Transaction.HASH_SIZE), extraTransactions.size());
                        /**/if (extraTransactions.size() > bestRating) {
                            /**/
                            /**/bestRating = extraTransactions.size();
                            /**/}
                    }
                }
            }
        }
        // System.out.ln(bestRating + " extra transactions approved");

        /**/if (tailsRaitings.isEmpty()) {
            /**/
            /**/return preferableMilestone;
            /**/}

        /**/final Map<Hash, Integer> filteredTailsRatings = new HashMap<>();
        /**/long totalSquaredRating = 0;
        /**/for (final Map.Entry<Hash, Integer> entry : tailsRaitings.entrySet()) {
            /**/
            /**/if (entry.getValue() >= bestRating * RATING_THRESHOLD / 100) {
                /**/
                /**/filteredTailsRatings.put(entry.getKey(), entry.getValue());
                /**/totalSquaredRating += ((long) entry.getValue()) * entry.getValue();
                /**/}
            /**/}
        /**/long hit = java.util.concurrent.ThreadLocalRandom.current().nextLong(totalSquaredRating);
        /**/for (final Map.Entry<Hash, Integer> entry : filteredTailsRatings.entrySet()) {
            /**/
            /**/if ((hit -= ((long) entry.getValue()) * entry.getValue()) < 0) {
                /**/
                /**/log.info(entry.getValue() + "/" + bestRating + " extra transactions approved");
                /**/return entry.getKey();
                /**/}
            /**/}

        /**/throw new RuntimeException("Must never be reached!");

        // return bestTip;
    }

    private static boolean setAnalyzedTransactionFlag(long pointer) {

        pointer -= Storage.CELLS_OFFSET - Storage.SUPER_GROUPS_OFFSET;

        final int value = analyzedTransactionsFlags[(int) (pointer >> (11 + 3))];
        if ((value & (1 << ((pointer >> 11) & 7))) == 0) {
            analyzedTransactionsFlags[(int) (pointer >> (11 + 3))] = (byte) (value | (1 << ((pointer >> 11) & 7)));
            return true;
        } else {
            return false;
        }
    }
    
    public void shutDown() {
        shuttingDown = true;
    }
    
    public static TipsManager instance() {
        return instance;
    }
    
    private TipsManager() {}
    
    private static TipsManager instance = new TipsManager();
}
