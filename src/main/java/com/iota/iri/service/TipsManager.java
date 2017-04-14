package com.iota.iri.service;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutionException;

import com.iota.iri.BundleValidator;
import com.iota.iri.Snapshot;
import com.iota.iri.model.Hash;
import com.iota.iri.service.viewModels.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Milestone;
import com.iota.iri.utils.Converter;

import static com.iota.iri.Snapshot.latestSnapshot;

public class TipsManager {

    private static final Logger log = LoggerFactory.getLogger(TipsManager.class);

    private static int RATING_THRESHOLD = 75; // Must be in [0..100] range
    
    private static int ARTIFICAL_LATENCY = 120; // in seconds 

    private static boolean shuttingDown;

    private static final Object updateSyncObject = new Object();
    private static final Snapshot stateSinceMilestone = new Snapshot(latestSnapshot);
    private static final Set<Hash> consistentHashes = new HashSet<>();
    private static volatile int numberOfConfirmedTransactions;

    public static void setRATING_THRESHOLD(int value) {
        if (value < 0) value = 0;
        if (value > 100) value = 100;
        RATING_THRESHOLD = value;
    }
    
    public static void setARTIFICAL_LATENCY(int value) {
        ARTIFICAL_LATENCY = value;
    }


    public void init() {
        try {
            log.info("Scanning Milestones...");
            scanMilestonesAndSnapshot();
        } catch (Exception e) {
            log.error("Could not finish milestone scan");
            e.printStackTrace();
        }
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

                    Milestone.instance().updateLatestMilestone();
                    Milestone.updateLatestSolidSubtangleMilestone();

                    if (previousLatestMilestoneIndex != Milestone.latestMilestoneIndex) {

                        log.info("Latest milestone has changed from #" + previousLatestMilestoneIndex
                                + " to #" + Milestone.latestMilestoneIndex);
                    }

                    long latency = 30000;
                    if (Milestone.latestSolidSubtangleMilestoneIndex > Milestone.MILESTONE_START_INDEX &&
                            Milestone.latestMilestoneIndex == Milestone.latestSolidSubtangleMilestoneIndex) {
                        latency = ARTIFICAL_LATENCY > 0 ? (long)(rnd.nextInt(ARTIFICAL_LATENCY))*1000L +5000L : 5000L;
                    }

                    long start = System.currentTimeMillis();
                    long cumulative = 0;
                    while((cumulative = System.currentTimeMillis() - start) < latency) {
                        if(Milestone.latestSolidSubtangleMilestoneIndex < Milestone.latestMilestoneIndex) {
                            Milestone.updateLatestSolidSubtangleMilestone();
                        } else {
                            break;
                        }
                    }

                    if (previousSolidSubtangleLatestMilestoneIndex != Milestone.latestSolidSubtangleMilestoneIndex) {
                        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(
                                Milestone.latestSolidSubtangleMilestoneIndex,
                                Milestone.latestSolidSubtangleMilestone);
                        milestoneViewModel.store();
                        updateSnapshot(milestoneViewModel);
                        synchronized (updateSyncObject) {
                            stateSinceMilestone.merge(latestSnapshot);
                        }

                        log.info("Latest SOLID SUBTANGLE milestone has changed from #"
                                + previousSolidSubtangleLatestMilestoneIndex + " to #"
                                + Milestone.latestSolidSubtangleMilestoneIndex);
                    }
                    latency -= cumulative;
                    if(latency > 0) {
                        Thread.sleep(latency - cumulative);
                    }
                    
                } catch (final Exception e) {
                    log.error("Error during TipsManager Milestone updating", e);
                }
            }
        }, "Latest Milestone Tracker")).start();
    }

    private static void scanMilestonesAndSnapshot() throws Exception {
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
        Snapshot updatedSnapshot = Snapshot.latestSnapshot;
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

    private static boolean updateSnapshot(MilestoneViewModel milestone) throws Exception {
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
                }
            }
        }
        return isConsistent;
    }

    private static boolean updateFromSnapshot(Hash tip) throws Exception {
        TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tip);
        boolean isConsistent = consistentHashes.contains(tip);
        if(!isConsistent) {
            Hash tail = transactionViewModel.getHash();
            Map<Hash, Long> currentState = getLatestDiff(tail, false);
            isConsistent = currentState != null && latestSnapshot.patch(currentState).isConsistent();
            if (isConsistent) {
                synchronized (updateSyncObject) {
                    updateConsistentHashes(tip);
                    stateSinceMilestone.merge(stateSinceMilestone.patch(currentState));
                }
            }
        }
        return isConsistent;
    }

    static Hash transactionToApprove(final Hash extraTip, final int depth, Random seed) {

        int milestoneDepth = depth;

        long startTime = System.nanoTime();

        if(Milestone.latestSolidSubtangleMilestoneIndex > Milestone.MILESTONE_START_INDEX ||
                Milestone.latestMilestoneIndex == Milestone.MILESTONE_START_INDEX) {
            final Hash preferableMilestone = Milestone.latestSolidSubtangleMilestone;

            Map<Hash, Integer> ratings = new HashMap<>();
            Set<Hash> analyzedTips = new HashSet<>();
            try {
                int traversedTails = 0;
                Hash tip = preferableMilestone;
                Hash tail = tip;
                if (extraTip != null) {
                    TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tip);
                    while (milestoneDepth-- > 0 && !tip.equals(Hash.NULL_HASH)) {

                        tip = transactionViewModel.getHash();
                        do {

                            transactionViewModel = transactionViewModel.getTrunkTransaction();

                        }
                        while (transactionViewModel.getCurrentIndex() != 0 && !transactionViewModel.getAddressHash().equals(Milestone.instance().coordinator()));
                    }
                }

                updateRatings(tip, ratings, analyzedTips);
                analyzedTips.clear();

                Hash[] tips;
                TransactionViewModel transactionViewModel;
                int carlo;
                double monte;
                while (tip != null) {
                    tips = TransactionViewModel.fromHash(tip).getApprovers();
                    if (tips.length == 0) {
                        break;
                    }
                    if (!ratings.containsKey(tip)) {
                        updateRatings(tip, ratings, analyzedTips);
                        analyzedTips.clear();
                    }
                    monte = seed.nextDouble() * ratings.get(tip);
                    for (carlo = tips.length; carlo-- > 1; ) {
                        if (ratings.containsKey(tips[carlo])) {
                            monte -= ratings.get(tips[carlo]);
                        }
                        if (monte <= 0) {
                            break;
                        }
                    }
                    transactionViewModel = TransactionViewModel.fromHash(tips[carlo]);
                    if (transactionViewModel == null) {
                        break;
                    } else if (!(checkSolidity(transactionViewModel.getHash()) && updateFromSnapshot(transactionViewModel.getHash()))) {
                        break;
                    } else if (transactionViewModel.getHash().equals(extraTip) || transactionViewModel.getHash().equals(tip)) {
                        break;
                    } else {
                        traversedTails++;
                        tip = transactionViewModel.getHash();
                        if(transactionViewModel.getCurrentIndex() == 0) {
                            tail = tip;
                        }
                    }
                }
                log.info("Tails traversed: " + traversedTails);
                return tail;
            } catch (Exception e) {
                e.printStackTrace();
                log.error("Encountered error: " + e.getLocalizedMessage());
            } finally {
                API.incEllapsedTime_getTxToApprove(System.nanoTime() - startTime);
            }
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

    private static int findOldestAcceptableMilestoneIndex(long criticalArrivalTime, int depth) throws Exception {
        int oldestAcceptableMilestoneIndex = Milestone.latestSolidSubtangleMilestoneIndex - depth;
        AddressViewModel coordinatorAddress = new AddressViewModel(Milestone.instance().coordinator());
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

                        final BundleValidator bundleValidator = new BundleValidator(transactionViewModel.getBundle());
                        for (final List<TransactionViewModel> bundleTransactionViewModels : bundleValidator.getTransactions()) {

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

            tailsToAnalyze.removeIf(analyzedTips::contains);
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

        log.debug("Confirmed transactions = " + numberOfAnalyzedTransactions);
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
    
    private static final TipsManager instance = new TipsManager();
}
