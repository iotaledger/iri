package com.iota.iri.service;

import java.util.*;

import com.iota.iri.LedgerValidator;
import com.iota.iri.model.Hash;
import com.iota.iri.controllers.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Milestone;

public class TipsManager {

    private static final Logger log = LoggerFactory.getLogger(TipsManager.class);

    private static int RATING_THRESHOLD = 75; // Must be in [0..100] range

    public static void setRATING_THRESHOLD(int value) {
        if (value < 0) value = 0;
        if (value > 100) value = 100;
        RATING_THRESHOLD = value;
    }
    
    static Hash transactionToApprove(final Hash extraTip, final int depth, Random seed) {

        int milestoneDepth = depth;

        long startTime = System.nanoTime();

        if(Milestone.latestSolidSubtangleMilestoneIndex > Milestone.MILESTONE_START_INDEX ||
                Milestone.latestMilestoneIndex == Milestone.MILESTONE_START_INDEX) {
            final Hash preferableMilestone = Milestone.latestSolidSubtangleMilestone;

            Map<Hash, Set<Hash>> ratings = new HashMap<>();
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

                    monte = seed.nextDouble() * Math.sqrt(ratings.get(tip).size());
                    for (carlo = tips.length; carlo-- > 1; ) {
                        if (ratings.containsKey(tips[carlo])) {
                            monte -= Math.sqrt(ratings.get(tips[carlo]).size());
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

    static Set<Hash> updateRatings(Hash txHash, Map<Hash, Set<Hash>> ratings, Set<Hash> analyzedTips) throws Exception {
        Set<Hash> rating;
        if(analyzedTips.add(txHash)) {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(txHash);
            rating = new HashSet<>(Collections.singleton(txHash));
            for(Hash approver : transactionViewModel.getApprovers()) {
                rating.addAll(updateRatings(approver, ratings, analyzedTips));
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

    static long updateLongRatings(Hash txHash, Map<Hash, Long> ratings, Set<Hash> analyzedTips) throws Exception {
        long rating = 1;
        if(analyzedTips.add(txHash)) {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(txHash);
            for(Hash approver : transactionViewModel.getApprovers()) {
                rating = capSum(rating, updateLongRatings(approver, ratings, analyzedTips), Long.MAX_VALUE/2);
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

    public static TipsManager instance() {
        return instance;
    }
    
    private TipsManager() {}
    
    private static final TipsManager instance = new TipsManager();
}
