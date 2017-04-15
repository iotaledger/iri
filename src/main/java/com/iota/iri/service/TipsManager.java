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

            Map<Hash, Long> ratings = new HashMap<>();
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

    private static long updateRatings(Hash txHash, Map<Hash, Long> ratings, Set<Hash> analyzedTips) throws Exception {
        long rating = 1;
        if(analyzedTips.add(txHash)) {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(txHash);
            for(Hash approver : transactionViewModel.getApprovers()) {
                rating += updateRatings(approver, ratings, analyzedTips);
            }
            if(rating > Long.MAX_VALUE/2) {
                rating = Long.MAX_VALUE/2;
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
