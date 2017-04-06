package com.iota.iri.service;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutionException;

import com.iota.iri.BundleValidator;
import com.iota.iri.model.Hash;
import com.iota.iri.service.viewModels.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Milestone;
import com.iota.iri.Snapshot;
import com.iota.iri.utils.Converter;

public class TipsManager {

    private static final Logger log = LoggerFactory.getLogger(TipsManager.class);

    private static int RATING_THRESHOLD = 75; // Must be in [0..100] range
    
    private static int ARTIFICAL_LATENCY = 120; // in seconds 

    static boolean shuttingDown;

    static int numberOfConfirmedTransactions;
    private static Hash lowestMilestone;

    public static enum Consistency {
        UNCHECKED,
        SNAPSHOT,
        INCONSISTENT,
        INCONSISTENT_SNAPSHOT
    };

    public static void setRATING_THRESHOLD(int value) {
        if (value < 0) value = 0;
        if (value > 100) value = 100;
        RATING_THRESHOLD = value;
    }
    
    public static void setARTIFICAL_LATENCY(int value) {
        ARTIFICAL_LATENCY = value;
    }

    private static final Map<Hash, Long> latestSnapshot = new HashMap<>(Snapshot.initialState);

    public void init() throws Exception {

        (new Thread(() -> {

            final SecureRandom rnd = new SecureRandom();

            while (!shuttingDown) {

                try {
                    TransactionRequester.instance().rescanTransactionsToRequest();
                } catch (ExecutionException e) {
                    log.error("Could not execute request rescan. ");
                } catch (InterruptedException e) {
                    log.error("Request rescan interrupted. ");
                }
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
                        updateSnapshot();

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

    private void updateSnapshot() throws Exception {
        Map<Hash, Long> currentState = getCurrentState(Milestone.latestSolidSubtangleMilestone, latestSnapshot);
        latestSnapshot.clear();
        latestSnapshot.putAll(currentState);
        TransactionViewModel.fromHash(Milestone.latestMilestone).updateConsistencies(Consistency.SNAPSHOT);
    }

    static Hash transactionToApprove(final Hash extraTip, final int depth, Random seed) {

        int milestoneDepth = depth;

        long startTime = System.nanoTime();

        final Hash preferableMilestone = Milestone.latestSolidSubtangleMilestone;

        Map<Hash, Integer> ratings = new HashMap<>();
        Map<Hash, Long> state = new HashMap<>();
        Set<Hash> analyzedTips = new HashSet<>();
        try {
            Hash tip = preferableMilestone;
            if (extraTip != null) {
                state = getCurrentState(extraTip, latestSnapshot);
                TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tip);
                while (milestoneDepth-- > 0 && !tip.equals(Hash.NULL_HASH)) {

                    tip = transactionViewModel.getHash();
                    do {

                        transactionViewModel = transactionViewModel.getTrunkTransaction();

                    } while (transactionViewModel.getCurrentIndex() != 0 && !transactionViewModel.getAddressHash().equals(Milestone.COORDINATOR));
                }
            }

            updateRatings(tip, ratings, analyzedTips);
            analyzedTips.clear();

            Hash[] tips;
            TransactionViewModel transactionViewModel;
            int carlo;
            double monte;
            while(tip != null) {
                tips = TransactionViewModel.fromHash(tip).getApprovers();
                if(tips.length == 0) {
                    break;
                }
                if(!ratings.containsKey(tip)) {
                    updateRatings(tip, ratings, analyzedTips);
                    analyzedTips.clear();
                }
                monte = seed.nextDouble() * ratings.get(tip);
                for(carlo = tips.length; carlo-- >  1;) {
                    if(ratings.containsKey(tips[carlo])) {
                        monte -= ratings.get(tips[carlo]);
                    }
                    if(monte <= 0 ) {
                        break;
                    }
                }
                transactionViewModel = TransactionViewModel.fromHash(tips[carlo]);
                state = getCurrentState(tips[carlo], state);
                if(!transactionViewModel.getBundle().isConsistent()
                        || !checkSolidity(tips[carlo])
                        || !ledgerIsConsistent(state)) {
                    break;
                } else if (tips[carlo].equals(extraTip)){
                    break;
                } else if (tips[carlo].equals(tip)){
                    break;
                } else {
                    tip = tips[carlo];
                }
            }
            return tip;
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Encountered error: " + e.getLocalizedMessage());
        } finally {
            API.incEllapsedTime_getTxToApprove(System.nanoTime() - startTime);
        }
        return null;
    }


    public static boolean checkSolidity(Hash hash) throws Exception {
        Set<Hash> analyzedHashes = new HashSet<>(Collections.singleton(Hash.NULL_HASH));
        boolean solid = true;
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(hash));
        Hash hashPointer, trunkInteger, branchInteger;
        while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
            if (analyzedHashes.add(hashPointer)) {
                final TransactionViewModel transactionViewModel2 = TransactionViewModel.fromHash(hashPointer);
                if(!transactionViewModel2.isSolid()) {
                    if (transactionViewModel2.getType() == TransactionViewModel.PREFILLED_SLOT && !hashPointer.equals(Hash.NULL_HASH)) {
                        TransactionRequester.instance().requestTransaction(hashPointer);
                        solid = false;
                        break;

                    } else {
                        trunkInteger = transactionViewModel2.getTrunkTransactionHash();
                        branchInteger = transactionViewModel2.getBranchTransactionHash();
                        nonAnalyzedTransactions.offer(trunkInteger);
                        nonAnalyzedTransactions.offer(branchInteger);
                    }
                }
            }
        }
        if (solid) {
            TransactionViewModel.updateSolidTransactions(analyzedHashes);
        }
        return solid;
    }

    private static int updateRatings(Hash txHash, Map<Hash, Integer> ratings, Set<Hash> analyzedTips) throws Exception {
        int rating = 1;
        if(analyzedTips.add(txHash)) {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(txHash);
            for(Hash approver : transactionViewModel.getApprovers()) {
                rating += updateRatings(approver, ratings, analyzedTips);
            }
            if(rating > Integer.MAX_VALUE/2) {
                rating = Integer.MAX_VALUE/2;
            }
            ratings.put(txHash, rating);
        } else {
            if(ratings.containsKey(txHash)) {
                rating = ratings.get(txHash);
            } else {
                rating = 0;
            }
        }
        return rating;       
    }

    private static int findOldestAcceptableMilestoneIndex(long criticalArrivalTime, int depth) throws Exception {
        int oldestAcceptableMilestoneIndex = Milestone.latestSolidSubtangleMilestoneIndex - depth;
        AddressViewModel coordinatorAddress = new AddressViewModel(Milestone.COORDINATOR);
        for (final Hash hash : coordinatorAddress.getTransactionHashes()) {
            final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(hash);
            if (transactionViewModel.getCurrentIndex() == 0) {
                int milestoneIndex = (int) Converter.longValue(transactionViewModel.trits(), TransactionViewModel.TAG_TRINARY_OFFSET,
                        15);
                if (milestoneIndex >= oldestAcceptableMilestoneIndex) {
                    long itsArrivalTime = transactionViewModel.getArrivalTime();
                    if (itsArrivalTime == 0)
                        itsArrivalTime = transactionViewModel.getTimestamp();
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

        return oldestAcceptableMilestoneIndex;
    }

    private static Hash findBestTip(Hash extraTip, Hash preferableMilestone, int bestRating, Map<Hash, Integer> tailsRatings) {
            /**/
        if (tailsRatings.isEmpty()) {
                /**/
            if (extraTip == null) {
                    /**/
                return preferableMilestone;
                    /**/
            }
                /**/
        }

            /**/
        final Map<Hash, Integer> filteredTailsRatings = new HashMap<>();
            /**/
        long totalSquaredRating = 0;
            /**/
        for (final Map.Entry<Hash, Integer> entry : tailsRatings.entrySet()) {
                /**/
                /**/
            if (entry.getValue() >= bestRating * RATING_THRESHOLD / 100) {
                    /**/
                    /**/
                filteredTailsRatings.put(entry.getKey(), entry.getValue());
                    /**/
                totalSquaredRating += ((long) entry.getValue()) * entry.getValue();
                    /**/
            }
                /**/
        }
            /**/
        if (totalSquaredRating > 0L) {
                /**/
            long hit = java.util.concurrent.ThreadLocalRandom.current().nextLong(totalSquaredRating);
                /**/
            for (final Map.Entry<Hash, Integer> entry : filteredTailsRatings.entrySet()) {
                    /**/
                    /**/
                if ((hit -= ((long) entry.getValue()) * entry.getValue()) < 0) {
                        /**/
                        /**/
                    log.info(entry.getValue() + "/" + bestRating + " extra transactions approved");
                        /**/
                    return entry.getKey();
                        /**/
                }
                    /**/
            }
                /**/
        }
            /**/
        else {
                /**/
            return preferableMilestone;
                /**/
        }
            /**/
        throw new RuntimeException("Must never be reached!");
        // return bestTip;
    }

    private static int getBestRating(Map<Hash, Long> state,
                                     Map<Hash, Integer> tailsRatings,
                                     Set<Hash> analyzedTips,
                                     Set<Hash> analyzedTipsCopy,
                                     List<Hash> tailsToAnalyze,
                                     final long criticalArrivalTime) throws Exception {

        /* --Coo only-- Hash bestTip = preferableMilestone; */
        int bestRating = 0;
        // final Set<Long> seenTails = new HashSet<>();

            /**/

        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>();
        Hash transactionPointer;
        for (int i = tailsToAnalyze.size(); i-- > 0; ) {

            final Hash tailHash = tailsToAnalyze.get(i);
                /*
                 * -- Coo only-- if (seenTails.contains(tailPointer)) {
                 *
                 * continue; }
                 */

            analyzedTips.clear();
            analyzedTips.addAll(analyzedTipsCopy);

            final Set<Hash> extraTransactions = new HashSet<>();

            nonAnalyzedTransactions.clear();
            nonAnalyzedTransactions.offer(tailHash);
            while ((transactionPointer = nonAnalyzedTransactions.poll()) != null) {

                if (analyzedTips.add(transactionPointer)) {

                    final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(transactionPointer);
                    if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {

                        // -- Coo only--
                        // seenTails.addAll(extraTransactions);

                        extraTransactions.clear();

                        break;

                    } else {

                        extraTransactions.add(transactionPointer);

                        nonAnalyzedTransactions.offer(transactionViewModel.getTrunkTransactionHash());
                        nonAnalyzedTransactions.offer(transactionViewModel.getBranchTransactionHash());
                    }
                }
            }

            if (extraTransactions.size() > /* bestRating */0) {

                Set<Hash> extraTransactionsCopy = new HashSet<>(extraTransactions);

                for (final Hash extraTransactionPointer : extraTransactions) {

                    final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(extraTransactionPointer);
                    if (transactionViewModel.getCurrentIndex() == 0) {

                        final BundleValidator bundleValidator = new BundleValidator(BundleViewModel.fromHash(transactionViewModel.getBundleHash()));
                        for (final List<TransactionViewModel> bundleTransactionViewModels : bundleValidator.getTransactions()) {

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

                    final Map<Hash, Long> stateCopy = new HashMap<>(state);

                    for (final Hash extraTransactionPointer : extraTransactions) {

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

                            /**/
                        tailsRatings
                                .put(tailHash, extraTransactions.size());
                            /**/
                        if (extraTransactions.size() > bestRating) {
                                /**/
                                /**/
                            bestRating = extraTransactions.size();
                                /**/
                        }
                    }
                }
            }
        }
        return bestRating;
    }

    private static void removeAnalyzedTips(Hash extraTip, List<Hash> tailsToAnalyze, Set<Hash> analyzedTips, Set<Hash> analyzedTipsCopy) {
        if (extraTip != null) {

            analyzedTips.clear();
            analyzedTips.addAll(analyzedTipsCopy);

            final Iterator<Hash> tailsToAnalyzeIterator = tailsToAnalyze.iterator();
            while (tailsToAnalyzeIterator.hasNext()) {

                final Hash tailHash = tailsToAnalyzeIterator.next();
                if (analyzedTips.contains(tailHash)) {
                    tailsToAnalyzeIterator.remove();
                }
            }
        }
    }

    private static void analyzeTips(Set<Hash> analyzedTips, List<Hash> tailsToAnalyze, Hash tip) throws Exception {
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
        final Set<Hash> tailsWithoutApprovers = new HashSet<>();
        Hash transactionPointer;
        while ((transactionPointer = nonAnalyzedTransactions.poll()) != null) {

            if (analyzedTips.add(transactionPointer)) {

                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(transactionPointer);

                if (transactionViewModel.getCurrentIndex() == 0 && !tailsToAnalyze.contains(transactionViewModel.getHash())) {

                    tailsToAnalyze.add(transactionViewModel.getHash());
                }

                final Hash approveePointer = Arrays.stream(transactionViewModel.getApprovers()).findFirst().orElse(null);
                if (approveePointer == null) {

                    if (transactionViewModel.getCurrentIndex() == 0) {

                        tailsWithoutApprovers.add(transactionPointer);
                    }

                } else {

                    for (final Hash approverPointer : TransactionViewModel.fromHash(approveePointer).getApprovers()) {
                        nonAnalyzedTransactions.offer(approverPointer);
                    }
                }
            }
        }
        tailsToAnalyze.removeAll(tailsWithoutApprovers); // Remove them from where they are...
        tailsToAnalyze.addAll(tailsWithoutApprovers);    // ...and add to the very end
    }

    private static boolean ledgerIsConsistent(Map<Hash, Long> state) {
        final Iterator<Map.Entry<Hash, Long>> stateIterator = state.entrySet().iterator();
        while (stateIterator.hasNext()) {

            final Map.Entry<Hash, Long> entry = stateIterator.next();
            if (entry.getValue() <= 0) {

                if (entry.getValue() < 0) {
                    log.info("Ledger inconsistency detected");
                    return false;
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
        return true;
    }

    public static Map<Hash,Long> getCurrentState(Hash tip, Map<Hash, Long> snapshot) throws Exception {
        Map<Hash, Long> state = new HashMap<>(snapshot);
        int numberOfAnalyzedTransactions = 0;
        Set<Hash> analyzedTips = new HashSet<>(Collections.singleton(Hash.NULL_HASH));

        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
        Hash transactionPointer;
        while ((transactionPointer = nonAnalyzedTransactions.poll()) != null) {

            if (analyzedTips.add(transactionPointer)) {

                numberOfAnalyzedTransactions++;

                final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(transactionPointer);
                if(transactionViewModel.getConsistency() != Consistency.SNAPSHOT) {
                    if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
                        TransactionRequester.instance().requestTransaction(transactionViewModel.getHash());
                        return null;

                    } else {

                        if (transactionViewModel.getCurrentIndex() == 0) {

                            boolean validBundle = false;

                            final BundleValidator bundleValidator = new BundleValidator(BundleViewModel.fromHash(transactionViewModel.getBundleHash()));
                            for (final List<TransactionViewModel> bundleTransactionViewModels : bundleValidator.getTransactions()) {

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

                            if (!validBundle || !bundleValidator.isConsistent()) {
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
                }
            }
        }

        log.info("Confirmed transactions = " + numberOfAnalyzedTransactions);
        if (tip == null) {
            numberOfConfirmedTransactions = numberOfAnalyzedTransactions;
        }
        return state;
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
