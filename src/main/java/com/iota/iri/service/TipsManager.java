package com.iota.iri.service;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.iota.iri.model.Flag;
import com.iota.iri.service.storage.AbstractStorage;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.service.viewModels.AddressViewModel;
import com.iota.iri.service.viewModels.TransactionViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Bundle;
import com.iota.iri.Milestone;
import com.iota.iri.Snapshot;
import com.iota.iri.model.Hash;
import com.iota.iri.utils.Converter;

public class TipsManager {

    private static final Logger log = LoggerFactory.getLogger(TipsManager.class);

    private static int RATING_THRESHOLD = 75; // Must be in [0..100] range
    
    private static int ARTIFICAL_LATENCY = 120; // in seconds 

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

                    long latency = 5000;
                    if (Milestone.latestSolidSubtangleMilestoneIndex > Milestone.MILESTONE_START_INDEX &&
                            Milestone.latestMilestoneIndex == Milestone.latestSolidSubtangleMilestoneIndex) {
                        latency = (long)((long)(rnd.nextInt(ARTIFICAL_LATENCY))*1000L)+5000L;
                    }
                    //log.info("Next milestone check in {} seconds",latency/1000L);
                    
                    Thread.sleep(latency);
                    
                } catch (final Exception e) {
                    log.error("Error during TipsManager Milestone updating", e);
                }
            }
        }, "Latest Milestone Tracker")).start();
    }

    static Hash transactionToApprove(final Hash extraTip, int depth) {

        long startTime = System.nanoTime();
                
        final Hash preferableMilestone = Milestone.latestSolidSubtangleMilestone;
        
        final int oldestAcceptableMilestoneIndex = Milestone.latestSolidSubtangleMilestoneIndex - depth;
        
        long criticalArrivalTime = Long.MAX_VALUE;

        Object transientHandle = Tangle.instance().createTransientList(Flag.class);
        try {
            AddressViewModel coordinatorAddress = new AddressViewModel(Milestone.COORDINATOR.bytes());
            //StorageAddresses.instance().addressesOf(Milestone.COORDINATOR)
            for (final Hash hash : coordinatorAddress.getTransactionHashes()) {
                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(hash);
                if (transactionViewModel.getCurrentIndex() == 0) {
                    int milestoneIndex = (int) Converter.longValue(transactionViewModel.trits(), TransactionViewModel.TAG_TRINARY_OFFSET,
                            15);
                    if (milestoneIndex >= oldestAcceptableMilestoneIndex) {
                        long itsArrivalTime = transactionViewModel.getArrivalTime();
                        final long timestamp = (int) Converter.longValue(transactionViewModel.trits(),
                                TransactionViewModel.TIMESTAMP_TRINARY_OFFSET, 27);
                        if (itsArrivalTime == 0)
                            itsArrivalTime = timestamp;
                        if (itsArrivalTime < criticalArrivalTime) {
                            criticalArrivalTime = itsArrivalTime;
                            // oldestAcceptableMilestone = new
                            // Hash(transactionViewModel.hash);
                        }
                    }
                }
            }

            // DateFormat formatter = new SimpleDateFormat("yyyy/MM/dd
            // HH:mm:ss");
            // Calendar calendar = Calendar.getInstance();
            // calendar.setTimeInMillis(criticalArrivalTime);
            // log.info("Oldest accepted solid milestone index
            // "+oldestAcceptableMilestoneIndex+", arrival time
            // "+formatter.format(calendar.getTime()));

            
            System.arraycopy(zeroedAnalyzedTransactionsFlags, 0, analyzedTransactionsFlags, 0, 134217728);

            Map<Hash, Long> state = new HashMap<>(Snapshot.initialState);

            {
                int numberOfAnalyzedTransactions = 0;

                /*
                final Queue<Long> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(StorageTransactions
                        .instance().transactionPointer((extraTip == null ? preferableMilestone : extraTip).bytes())));
                        */
                final Queue<byte[]> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(TransactionViewModel.fromHash(extraTip == null ? preferableMilestone : extraTip).getHash()));
                byte[] transactionHash;
                while ((transactionHash = nonAnalyzedTransactions.poll()) != null) {

                    if (setAnalyzedTransactionFlag(transientHandle, transactionHash)) {

                        numberOfAnalyzedTransactions++;

                        final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(transactionHash);
                        if (transactionViewModel.type == AbstractStorage.PREFILLED_SLOT) {

                            return null;

                        } else {

                            if (transactionViewModel.getCurrentIndex() == 0) {

                                boolean validBundle = false;

                                final Bundle bundle = new Bundle(transactionViewModel.getBundleHash());
                                for (final List<TransactionViewModel> bundleTransactionViewModels : bundle.getTransactions()) {

                                    if (bundleTransactionViewModels.get(0).getHash().equals(transactionViewModel.getHash())) {

                                        validBundle = true;

                                        for (final TransactionViewModel bundleTransactionViewModel : bundleTransactionViewModels) {

                                            if (bundleTransactionViewModel.value() != 0) {

                                                final Hash address = bundleTransactionViewModel.getAddress().getHash();
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
                
                log.info("Confirmed transactions = " + numberOfAnalyzedTransactions);
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
                 * System.out.ln("initialState.put(new Hash(\"" + entry.getKey()
                 * + "\"), " + entry.getValue() + "L);"); }
                 */
                ////////////
            }

            System.arraycopy(analyzedTransactionsFlags, 0, analyzedTransactionsFlagsCopy, 0, 134217728);
            System.arraycopy(zeroedAnalyzedTransactionsFlags, 0, analyzedTransactionsFlags, 0, 134217728);

            final List<byte[]> tailsToAnalyze = new LinkedList<>();

            byte[] tip = preferableMilestone.bytes(); //StorageTransactions.instance().transactionPointer(preferableMilestone.bytes());
            if (extraTip != null) {

                TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tip);//StorageTransactions.instance().loadTransaction(tip);
                //while (depth-- > 0 && tip != Storage.CELLS_OFFSET - Storage.SUPER_GROUPS_OFFSET) {
                while (depth-- > 0 && tip != Hash.NULL_HASH.bytes()) {

                    tip = transactionViewModel.getHash();
                    do {

                        transactionViewModel = transactionViewModel.getTrunkTransaction();

                    } while (transactionViewModel.getCurrentIndex() != 0);
                }
            }
            
            final Queue<byte[]> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
            byte[] transactionHash;
            final Set<byte[]> tailsWithoutApprovers = new HashSet<>();
            while ((transactionHash = nonAnalyzedTransactions.poll()) != null) {

                if (setAnalyzedTransactionFlag(transientHandle, transactionHash)) {

                    final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(transactionHash);

                    if (transactionViewModel.getCurrentIndex() == 0 && !tailsToAnalyze.contains(transactionViewModel.getHash())) {

                        tailsToAnalyze.add(transactionViewModel.getHash());
                    }

                    final byte[] approveePointer = transactionViewModel.getApprovers()[0].bytes();// StorageApprovers.instance().approveePointer(transactionViewModel.getHash());
                    if (approveePointer == null) {

                        if (transactionViewModel.getCurrentIndex() == 0) {

                            tailsWithoutApprovers.add(transactionHash);
                        }

                    } else {

                        for (final Hash approverPointer : TransactionViewModel.fromHash(approveePointer).getApprovers()) {
                            nonAnalyzedTransactions.offer(approverPointer.bytes());
                        }
                    }
                }
            }
            tailsToAnalyze.removeAll(tailsWithoutApprovers); // Remove them from where they are...
            tailsToAnalyze.addAll(tailsWithoutApprovers);    // ...and add to the very end

            if (extraTip != null) {

                System.arraycopy(analyzedTransactionsFlagsCopy, 0, analyzedTransactionsFlags, 0, 134217728);

                final Iterator<byte[]> tailsToAnalyzeIterator = tailsToAnalyze.iterator();
                while (tailsToAnalyzeIterator.hasNext()) {

                    final byte[] tailHash = tailsToAnalyzeIterator.next();
                    try {
                        if (Tangle.instance().maybeHas(transientHandle, tailHash).get()) {
                            if (Tangle.instance().load(transientHandle, Flag.class, tailHash).get() != null) {
                                tailsToAnalyzeIterator.remove();
                            }
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }

                    /*
                    long tailPointer = StorageTransactions.instance().transactionPointer(tailHash);
                    if ((analyzedTransactionsFlags[(int) ((tailPointer
                            - (Storage.CELLS_OFFSET - Storage.SUPER_GROUPS_OFFSET)) >> (11 + 3))]
                            & (1 << (((tailPointer - (Storage.CELLS_OFFSET - Storage.SUPER_GROUPS_OFFSET)) >> 11)
                                    & 7))) != 0) {

                        tailsToAnalyzeIterator.remove();
                    }
                    */
                }
            }

            log.info(tailsToAnalyze.size() + " tails need to be analyzed");

            /* --Coo only-- Hash bestTip = preferableMilestone; */
            int bestRating = 0;
            // final Set<Long> seenTails = new HashSet<>();

            /**/final Map<Hash, Integer> tailsRaitings = new HashMap<>();

            for (int i = tailsToAnalyze.size(); i-- > 0;) {

                final byte[] tailHash = tailsToAnalyze.get(i);
                /*
                 * -- Coo only-- if (seenTails.contains(tailPointer)) {
                 * 
                 * continue; }
                 */

                System.arraycopy(analyzedTransactionsFlagsCopy, 0, analyzedTransactionsFlags, 0, 134217728);

                final Set<byte[]> extraTransactions = new HashSet<>();

                nonAnalyzedTransactions.clear();
                nonAnalyzedTransactions.offer(tailHash);
                while ((transactionHash = nonAnalyzedTransactions.poll()) != null) {

                    if (setAnalyzedTransactionFlag(transientHandle, transactionHash)) {

                        final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(transactionHash);
                        if (transactionViewModel.type == AbstractStorage.PREFILLED_SLOT) {

                            // -- Coo only--
                            // seenTails.addAll(extraTransactions);

                            extraTransactions.clear();

                            break;

                        } else {

                            extraTransactions.add(transactionHash);

                            nonAnalyzedTransactions.offer(transactionViewModel.getTrunkTransactionHash());
                            nonAnalyzedTransactions.offer(transactionViewModel.getBranchTransactionHash());
                        }
                    }
                }

                if (extraTransactions.size() > /* bestRating */0) {

                    Set<byte[]> extraTransactionsCopy = new HashSet<>(extraTransactions);

                    for (final byte[] extraTransactionPointer : extraTransactions) {

                        final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(extraTransactionPointer);
                        if (transactionViewModel.getCurrentIndex() == 0) {

                            final Bundle bundle = new Bundle(transactionViewModel.getBundleHash());
                            for (final List<TransactionViewModel> bundleTransactionViewModels : bundle.getTransactions()) {

                                if (bundleTransactionViewModels.get(0).getHash().equals(transactionViewModel.getHash())) {

                                    for (final TransactionViewModel bundleTransactionViewModel : bundleTransactionViewModels) {

                                        final long timestamp = (int) Converter.longValue(bundleTransactionViewModel.trits(),
                                                TransactionViewModel.TIMESTAMP_TRINARY_OFFSET, 27);
                                        long itsArrivalTime = bundleTransactionViewModel.getArrivalTime();
                                        if (itsArrivalTime == 0)
                                            itsArrivalTime = timestamp;

                                        if (itsArrivalTime < criticalArrivalTime) {
                                            extraTransactionsCopy = null;
                                            break;
                                        }

                                        if (!extraTransactionsCopy.remove(bundleTransactionViewModel.getHash())) {
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

                        for (final byte[] extraTransactionPointer : extraTransactions) {

                            final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(extraTransactionPointer);
                            if (transactionViewModel.value() != 0) {

                                final Hash address = transactionViewModel.getAddress().getHash();
                                final Long value = stateCopy.get(address);
                                stateCopy.put(address, value == null ? transactionViewModel.value() : (value + transactionViewModel.value()));
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
                            // bestTip = new Hash(Storage.loadTransaction(tailPointer).hash, 0, TransactionViewModel.HASH_SIZE);
                            // bestRating = extraTransactions.size();
                            // seenTails.addAll(extraTransactions);

                            /**/tailsRaitings
                                    .put(new Hash(tailHash, 0,
                                            TransactionViewModel.HASH_SIZE), extraTransactions.size());
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
                /**/if (extraTip == null) {
                    /**/ return preferableMilestone;
                    /**/}
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
            /**/if (totalSquaredRating > 0L) {
                /**/long hit = java.util.concurrent.ThreadLocalRandom.current().nextLong(totalSquaredRating);
                /**/for (final Map.Entry<Hash, Integer> entry : filteredTailsRatings.entrySet()) {
                    /**/
                    /**/if ((hit -= ((long) entry.getValue()) * entry.getValue()) < 0) {
                        /**/
                        /**/log.info(entry.getValue() + "/" + bestRating + " extra transactions approved");
                        /**/return entry.getKey();
                        /**/}
                    /**/}
                /**/}
            /**/else {
                /**/return preferableMilestone;
                /**/}

            /**/throw new RuntimeException("Must never be reached!");
            // return bestTip;

        } finally {
            API.incEllapsedTime_getTxToApprove(System.nanoTime() - startTime);
            try {
                Tangle.instance().dropList(transientHandle);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean setAnalyzedTransactionFlag(Object handle, byte[] hash) {

        try {
            if(Tangle.instance().maybeHas(handle, hash).get()) {
                if(Tangle.instance().load(handle, Flag.class, hash).get() != null) {
                    return false;
                }
            }
            Tangle.instance().save(new Flag(hash)).get();
            return true;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return false;
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
