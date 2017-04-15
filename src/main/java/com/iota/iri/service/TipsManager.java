package com.iota.iri.service;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutionException;

import com.iota.iri.LedgerValidator;
import com.iota.iri.model.Hash;
import com.iota.iri.service.viewModels.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Milestone;

import static com.iota.iri.Snapshot.latestSnapshot;

public class TipsManager {

    private static final Logger log = LoggerFactory.getLogger(TipsManager.class);

    private static int RATING_THRESHOLD = 75; // Must be in [0..100] range
    
    private static int ARTIFICAL_LATENCY = 120; // in seconds 

    private static boolean shuttingDown;


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
                        LedgerValidator.updateSnapshot(milestoneViewModel);

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
                    } else if (!(checkSolidity(transactionViewModel.getHash()) && LedgerValidator.updateFromSnapshot(transactionViewModel.getHash()))) {
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

    public void shutDown() {
        shuttingDown = true;
    }
    
    public static TipsManager instance() {
        return instance;
    }
    
    private TipsManager() {}
    
    private static final TipsManager instance = new TipsManager();
}
