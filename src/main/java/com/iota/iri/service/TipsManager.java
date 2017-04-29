package com.iota.iri.service;

import java.util.*;
import java.util.concurrent.ExecutionException;

import com.iota.iri.LedgerValidator;
import com.iota.iri.model.Hash;
import com.iota.iri.controllers.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Milestone;

public class TipsManager {

    private static final Logger log = LoggerFactory.getLogger(TipsManager.class);

    private static int RATING_THRESHOLD = 75; // Must be in [0..100] range
    private boolean shuttingDown = false;
    private static int RESCAN_TX_TO_REQUEST_INTERVAL = 6000;
    private Thread solidityRescanHandle;

    public static void setRATING_THRESHOLD(int value) {
        if (value < 0) value = 0;
        if (value > 100) value = 100;
        RATING_THRESHOLD = value;
    }
    public void init() {
        solidityRescanHandle = new Thread(() -> {

            while(!shuttingDown) {
                Arrays.stream(TipsViewModel.getTips()).forEach(t -> {
                    try {
                        TransactionRequester.instance().checkSolidity(t, false);
                    } catch (Exception e) {
                        log.error("Error during solidity scan for {}: {}", t, e);
                    }
                });
                try {
                    Thread.sleep(RESCAN_TX_TO_REQUEST_INTERVAL);
                } catch (InterruptedException e) {
                    log.error("Solidity rescan interrupted.");
                }
            }
        }, "Tip Solidity Rescan");
        solidityRescanHandle.start();
    }
    public void shutdown() throws InterruptedException {
        shuttingDown = true;
        solidityRescanHandle.join();
    }

    static Hash transactionToApprove(final Hash extraTip, final int depth, Random seed) {

        int milestoneDepth = depth;

        long startTime = System.nanoTime();

        if(Milestone.latestSolidSubtangleMilestoneIndex > Milestone.MILESTONE_START_INDEX ||
                Milestone.latestMilestoneIndex == Milestone.MILESTONE_START_INDEX) {
            final Hash preferableMilestone = Milestone.latestSolidSubtangleMilestone;

            Map<Hash, Long> ratings = new HashMap<>();
            Set<Hash> analyzedTips = new HashSet<>();
            try {
                int traversedTails = 0;
                Hash tip = preferableMilestone;
                if (extraTip != null) {
                    int milestoneIndex = Milestone.latestSolidSubtangleMilestoneIndex - milestoneDepth;
                    if(milestoneIndex < 0) {
                        milestoneIndex = 0;
                    }
                    if(!MilestoneViewModel.load(milestoneIndex)) {
                        Map.Entry<Integer, Hash> closestGreaterMilestone = Milestone.findMilestone(milestoneIndex);
                        new MilestoneViewModel(closestGreaterMilestone.getKey(), closestGreaterMilestone.getValue()).store();
                        tip = closestGreaterMilestone.getValue();
                    } else {
                        tip = MilestoneViewModel.get(milestoneIndex).getHash();
                    }
                }
                Hash tail = tip;

                serialUpdateRatings(tip, ratings, analyzedTips);
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
                        serialUpdateRatings(tip, ratings, analyzedTips);
                        analyzedTips.clear();
                    }

                    monte = seed.nextDouble() * Math.sqrt(ratings.get(tip));
                    for (carlo = tips.length; carlo-- > 1; ) {
                        if (ratings.containsKey(tips[carlo])) {
                            monte -= Math.sqrt(ratings.get(tips[carlo]));
                        }
                        if (monte <= 0) {
                            break;
                        }
                    }
                    transactionViewModel = TransactionViewModel.fromHash(tips[carlo]);
                    if (transactionViewModel == null) {
                        break;
                    } else if (!(TransactionRequester.instance().checkSolidity(transactionViewModel.getHash(), false) &&
                            LedgerValidator.updateFromSnapshot(transactionViewModel.getHash()))) {
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
                log.info("Tx traversed to find tip: " + traversedTails);
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

    static long capSum(long a, long b, long max) {
        if(a + b < 0 || a + b > max) {
            return max;
        }
        return a+b;
    }

    static void serialUpdateRatings(final Hash txHash, final Map<Hash, Long> ratings, final Set<Hash> analyzedTips) throws Exception {
        Stack<Hash> hashesToRate = new Stack<>();
        hashesToRate.push(txHash);
        Hash currentHash;
        boolean addedBack;
        while(!hashesToRate.empty()) {
            currentHash = hashesToRate.pop();
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(currentHash);
            addedBack = false;
            Hash[] approvers = transactionViewModel.getApprovers();
            for(Hash approver : approvers) {
                if(ratings.get(approver) == null && !approver.equals(currentHash)) {
                    if(!addedBack) {
                        addedBack = true;
                        hashesToRate.push(currentHash);
                    }
                    hashesToRate.push(approver);
                }
            }
            if(!addedBack && analyzedTips.add(currentHash)) {
                ratings.put(currentHash, 1 + Arrays.stream(approvers).map(ratings::get).filter(Objects::nonNull)
                        .reduce((a, b) -> capSum(a,b, Long.MAX_VALUE/2)).orElse(0L));
            }
        }
    }

    static Set<Hash> updateHashRatings(Hash txHash, Map<Hash, Set<Hash>> ratings, Set<Hash> analyzedTips) throws Exception {
        Set<Hash> rating;
        if(analyzedTips.add(txHash)) {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(txHash);
            rating = new HashSet<>(Collections.singleton(txHash));
            for(Hash approver : transactionViewModel.getApprovers()) {
                rating.addAll(updateHashRatings(approver, ratings, analyzedTips));
            }
            ratings.put(txHash, rating);
        } else {
            if(ratings.containsKey(txHash)) {
                rating = ratings.get(txHash);
            } else {
                rating = new HashSet<>();
            }
        }
        return rating;       
    }

    static long recursiveUpdateRatings(Hash txHash, Map<Hash, Long> ratings, Set<Hash> analyzedTips) throws Exception {
        long rating = 1;
        if(analyzedTips.add(txHash)) {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(txHash);
            for(Hash approver : transactionViewModel.getApprovers()) {
                rating = capSum(rating, recursiveUpdateRatings(approver, ratings, analyzedTips), Long.MAX_VALUE/2);
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

    private TipsManager() {}
    
    public static final TipsManager instance = new TipsManager();
}
