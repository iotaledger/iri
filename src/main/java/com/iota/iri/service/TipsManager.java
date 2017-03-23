package com.iota.iri.service;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutionException;

import com.iota.iri.model.Flag;
import com.iota.iri.service.storage.AbstractStorage;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.service.viewModels.AddressViewModel;
import com.iota.iri.service.viewModels.BundleViewModel;
import com.iota.iri.service.viewModels.TransactionViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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


    public static void setRATING_THRESHOLD(int value) {
        if (value < 0) value = 0;
        if (value > 100) value = 100;
        RATING_THRESHOLD = value;
    }
    
    public static void setARTIFICAL_LATENCY(int value) {
        ARTIFICAL_LATENCY = value;
    }
    
    public void init() throws Exception {

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

                    long latency = 30000;
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

    static BigInteger transactionToApprove(final BigInteger extraTip, int depth) throws Exception {

        long startTime = System.nanoTime();
                
        final BigInteger preferableMilestone = Milestone.latestSolidSubtangleMilestone;
        
        final int oldestAcceptableMilestoneIndex = Milestone.latestSolidSubtangleMilestoneIndex - depth;
        
        long criticalArrivalTime = Long.MAX_VALUE;

        int transientHandle = Tangle.instance().createTransientFlagList();
        int transientHandleCopy = Tangle.instance().createTransientFlagList();
        try {
            AddressViewModel coordinatorAddress = new AddressViewModel(Milestone.COORDINATOR.bytes());
            for (final BigInteger hash : coordinatorAddress.getTransactionHashes()) {
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

            
            Map<BigInteger, Long> state = new HashMap<>(Snapshot.initialState);

            {
                int numberOfAnalyzedTransactions = 0;

                setAnalyzedTransactionFlag(transientHandle, new BigInteger(TransactionViewModel.NULL_TRANSACTION_HASH_BYTES));
                final Queue<BigInteger> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(TransactionViewModel.fromHash(extraTip == null ? preferableMilestone : extraTip).getHash()));
                BigInteger transactionPointer;
                Hash transactionHash, bundleHash;
                while ((transactionPointer = nonAnalyzedTransactions.poll()) != null) {

                    if (setAnalyzedTransactionFlag(transientHandle, transactionPointer)) {

                        numberOfAnalyzedTransactions++;

                        transactionHash = new Hash(Hash.padHash(transactionPointer));
                        final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(transactionPointer);
                        if (transactionViewModel.getType() == AbstractStorage.PREFILLED_SLOT) {
                            ScratchpadViewModel.instance().requestTransaction(transactionViewModel.getHash());
                            Tangle.instance().releaseTransientTable(transientHandle);
                            return null;

                        } else {

                            if (transactionViewModel.getCurrentIndex() == 0) {

                                boolean validBundle = false;

                                bundleHash = new Hash(transactionViewModel.getBundleHash());
                                final BundleViewModel bundle = BundleViewModel.fromHash(transactionViewModel.getBundleHash());
                                for (final List<TransactionViewModel> bundleTransactionViewModels : bundle.getTransactions()) {

                                    if (bundleTransactionViewModels.get(0).getHash().equals(transactionViewModel.getHash())) {

                                        validBundle = true;

                                        for (final TransactionViewModel bundleTransactionViewModel : bundleTransactionViewModels) {

                                            if (bundleTransactionViewModel.value() != 0) {

                                                final BigInteger address = bundleTransactionViewModel.getAddress().getHash();
                                                final Long value = state.get(address);
                                                state.put(address, value == null ? bundleTransactionViewModel.value()
                                                        : (value + bundleTransactionViewModel.value()));
                                            }
                                        }

                                        break;
                                    }
                                }

                                if (!validBundle) {
                                    Tangle.instance().releaseTransientTable(transientHandle);
                                    return null;
                                }
                            }

                            nonAnalyzedTransactions.offer(transactionViewModel.getTrunkTransactionPointer());
                            nonAnalyzedTransactions.offer(transactionViewModel.getBranchTransactionPointer());
                        }
                    }
                }
                
                log.info("Confirmed transactions = " + numberOfAnalyzedTransactions);
                if (extraTip == null) {
                    numberOfConfirmedTransactions = numberOfAnalyzedTransactions;
                }
            }

            final Iterator<Map.Entry<BigInteger, Long>> stateIterator = state.entrySet().iterator();
            while (stateIterator.hasNext()) {

                final Map.Entry<BigInteger, Long> entry = stateIterator.next();
                if (entry.getValue() <= 0) {

                    if (entry.getValue() < 0) {
                        log.info("Ledger inconsistency detected");
                        Tangle.instance().releaseTransientTable(transientHandle);
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

            Tangle.instance().flushTransientFlags(transientHandleCopy).get();
            Tangle.instance().copyTransientList(transientHandle, transientHandleCopy).get();
            Tangle.instance().flushTransientFlags(transientHandle).get();

            final List<BigInteger> tailsToAnalyze = new LinkedList<>();

            BigInteger tip = preferableMilestone; //StorageTransactions.instance().transactionPointer(preferableMilestone.value());
            if (extraTip != null) {

                TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tip);//StorageTransactions.instance().loadTransaction(tip);
                while (depth-- > 0 && !tip.equals(TransactionViewModel.PADDED_NULL_HASH)) {

                    tip = transactionViewModel.getHash();
                    do {

                        transactionViewModel = transactionViewModel.getTrunkTransaction();

                    } while (transactionViewModel.getCurrentIndex() != 0);
                }
            }
            
            final Queue<BigInteger> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
            BigInteger transactionPointer;
            final Set<BigInteger> tailsWithoutApprovers = new HashSet<>();
            while ((transactionPointer = nonAnalyzedTransactions.poll()) != null) {

                if (setAnalyzedTransactionFlag(transientHandle, transactionPointer)) {

                    final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(transactionPointer);

                    if (transactionViewModel.getCurrentIndex() == 0 && !tailsToAnalyze.contains(transactionViewModel.getHash())) {

                        tailsToAnalyze.add(transactionViewModel.getHash());
                    }

                    final BigInteger approveePointer = Arrays.stream(transactionViewModel.getApprovers()).findFirst().orElse(null);
                    if (approveePointer == null) {

                        if (transactionViewModel.getCurrentIndex() == 0) {

                            tailsWithoutApprovers.add(transactionPointer);
                        }

                    } else {

                        for (final BigInteger approverPointer : TransactionViewModel.fromHash(approveePointer).getApprovers()) {
                            nonAnalyzedTransactions.offer(approverPointer);
                        }
                    }
                }
            }
            tailsToAnalyze.removeAll(tailsWithoutApprovers); // Remove them from where they are...
            tailsToAnalyze.addAll(tailsWithoutApprovers);    // ...and add to the very end

            if (extraTip != null) {

                Tangle.instance().flushTransientFlags(transientHandle).get();
                Tangle.instance().copyTransientList(transientHandleCopy, transientHandle).get();

                final Iterator<BigInteger> tailsToAnalyzeIterator = tailsToAnalyze.iterator();
                while (tailsToAnalyzeIterator.hasNext()) {

                    final byte[] tailHash = Hash.padHash(tailsToAnalyzeIterator.next());
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
                }
            }

            log.info(tailsToAnalyze.size() + " tails need to be analyzed");

            /* --Coo only-- Hash bestTip = preferableMilestone; */
            int bestRating = 0;
            // final Set<Long> seenTails = new HashSet<>();

            /**/final Map<BigInteger, Integer> tailsRaitings = new HashMap<>();

            for (int i = tailsToAnalyze.size(); i-- > 0;) {

                final BigInteger tailHash = tailsToAnalyze.get(i);
                /*
                 * -- Coo only-- if (seenTails.contains(tailPointer)) {
                 * 
                 * continue; }
                 */

                Tangle.instance().flushTransientFlags(transientHandle).get();
                Tangle.instance().copyTransientList(transientHandleCopy, transientHandle).get();

                final Set<BigInteger> extraTransactions = new HashSet<>();

                nonAnalyzedTransactions.clear();
                nonAnalyzedTransactions.offer(tailHash);
                while ((transactionPointer = nonAnalyzedTransactions.poll()) != null) {

                    if (setAnalyzedTransactionFlag(transientHandle, transactionPointer)) {

                        final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(transactionPointer);
                        if (transactionViewModel.getType() == AbstractStorage.PREFILLED_SLOT) {

                            // -- Coo only--
                            // seenTails.addAll(extraTransactions);

                            extraTransactions.clear();

                            break;

                        } else {

                            extraTransactions.add(transactionPointer);

                            nonAnalyzedTransactions.offer(transactionViewModel.getTrunkTransactionPointer());
                            nonAnalyzedTransactions.offer(transactionViewModel.getBranchTransactionPointer());
                        }
                    }
                }

                if (extraTransactions.size() > /* bestRating */0) {

                    Set<BigInteger> extraTransactionsCopy = new HashSet<>(extraTransactions);

                    for (final BigInteger extraTransactionPointer : extraTransactions) {

                        final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(extraTransactionPointer);
                        if (transactionViewModel.getCurrentIndex() == 0) {

                            final BundleViewModel bundle = BundleViewModel.fromHash(transactionViewModel.getBundleHash());
                            for (final List<TransactionViewModel> bundleTransactionViewModels : bundle.getTransactions()) {

                                //if (Arrays.equals(bundleTransactionViewModels.get(0).getHash(), transactionViewModel.getHash())) {
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

                        final Map<BigInteger, Long> stateCopy = new HashMap<>(state);

                        for (final BigInteger extraTransactionPointer : extraTransactions) {

                            final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(extraTransactionPointer);
                            if (transactionViewModel.value() != 0) {

                                final BigInteger address = transactionViewModel.getAddress().getHash();
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
                                    .put(tailHash, extraTransactions.size());
                            /**/if (extraTransactions.size() > bestRating) {
                                /**/
                                /**/bestRating = extraTransactions.size();
                                /**/}
                        }
                    }
                }
            }
            Tangle.instance().releaseTransientTable(transientHandle);
            Tangle.instance().releaseTransientTable(transientHandleCopy);
            // System.out.ln(bestRating + " extra transactions approved");

            /**/if (tailsRaitings.isEmpty()) {
                /**/if (extraTip == null) {
                    /**/ return preferableMilestone;
                    /**/}
                /**/}

            /**/final Map<BigInteger, Integer> filteredTailsRatings = new HashMap<>();
            /**/long totalSquaredRating = 0;
            /**/for (final Map.Entry<BigInteger, Integer> entry : tailsRaitings.entrySet()) {
                /**/
                /**/if (entry.getValue() >= bestRating * RATING_THRESHOLD / 100) {
                    /**/
                    /**/filteredTailsRatings.put(entry.getKey(), entry.getValue());
                    /**/totalSquaredRating += ((long) entry.getValue()) * entry.getValue();
                    /**/}
                /**/}
            /**/if (totalSquaredRating > 0L) {
                /**/long hit = java.util.concurrent.ThreadLocalRandom.current().nextLong(totalSquaredRating);
                /**/for (final Map.Entry<BigInteger, Integer> entry : filteredTailsRatings.entrySet()) {
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

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            API.incEllapsedTime_getTxToApprove(System.nanoTime() - startTime);
        }
        return null;
    }

    private static boolean setAnalyzedTransactionFlag(int handle, BigInteger hashPointer) throws ExecutionException, InterruptedException {
        BigInteger hash = hashPointer;
        if(!Tangle.instance().maybeHas(handle, hash).get()) {
            Tangle.instance().save(handle, new Flag(hash)).get();
            return true;
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
