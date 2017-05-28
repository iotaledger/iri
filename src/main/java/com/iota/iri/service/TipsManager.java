package com.iota.iri.service;

import java.util.*;

import com.iota.iri.Iota;
import com.iota.iri.LedgerValidator;
import com.iota.iri.TransactionValidator;
import com.iota.iri.model.Hash;
import com.iota.iri.controllers.*;
import com.iota.iri.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Milestone;

public class TipsManager {

    private final Logger log = LoggerFactory.getLogger(TipsManager.class);
    private final Tangle tangle;
    private final TipsViewModel tipsViewModel;
    private final Milestone milestone;
    private final LedgerValidator ledgerValidator;
    private final TransactionValidator transactionValidator;

    private int RATING_THRESHOLD = 75; // Must be in [0..100] range
    private boolean shuttingDown = false;
    private int RESCAN_TX_TO_REQUEST_INTERVAL = 750;
    private Thread solidityRescanHandle;

    public void setRATING_THRESHOLD(int value) {
        if (value < 0) value = 0;
        if (value > 100) value = 100;
        RATING_THRESHOLD = value;
    }

    public TipsManager(final Tangle tangle,
                       final LedgerValidator ledgerValidator,
                       final TransactionValidator transactionValidator,
                       final TipsViewModel tipsViewModel,
                       final Milestone milestone) {
        this.tangle = tangle;
        this.ledgerValidator = ledgerValidator;
        this.transactionValidator = transactionValidator;
        this.tipsViewModel = tipsViewModel;
        this.milestone = milestone;
    }

    public void init() {
        solidityRescanHandle = new Thread(() -> {

            while(!shuttingDown) {
                try {
                    scanTipsForSolidity();
                } catch (Exception e) {
                    log.error("Error during solidity scan : {}", e);
                }
                try {
                    Thread.sleep(RESCAN_TX_TO_REQUEST_INTERVAL);
                } catch (InterruptedException e) {
                    log.error("Solidity rescan interrupted.");
                }
            }
        }, "Tip Solidity Rescan");
        solidityRescanHandle.start();
    }

    private void scanTipsForSolidity() throws Exception {
        int size = tipsViewModel.nonSolidSize();
        if(size != 0) {
            Hash hash = tipsViewModel.getRandomNonSolidTipHash();
            boolean isTip = true;
            if(hash != null && TransactionViewModel.fromHash(tangle, hash).getApprovers(tangle).size() != 0) {
                tipsViewModel.removeTipHash(hash);
                isTip = false;
            }
            if(hash != null && transactionValidator.checkSolidity(hash, false) && isTip) {
                //if(hash != null && TransactionViewModel.fromHash(hash).isSolid() && isTip) {
                tipsViewModel.setSolid(hash);
            }
        }
    }

    public void shutdown() throws InterruptedException {
        shuttingDown = true;
        try {
            if (solidityRescanHandle != null && solidityRescanHandle.isAlive())
                solidityRescanHandle.join();
        }
        catch (Exception e) {
            log.error("Error in shutdown",e);
        }
            
    }

    Hash transactionToApprove(final Hash extraTip, final int depth, Random seed) {


        long startTime = System.nanoTime();

        if(milestone.latestSolidSubtangleMilestoneIndex > Milestone.MILESTONE_START_INDEX ||
                milestone.latestMilestoneIndex == Milestone.MILESTONE_START_INDEX) {
            final Hash preferableMilestone = milestone.latestSolidSubtangleMilestone;

            Map<Hash, Long> ratings = new HashMap<>();
            Set<Hash> analyzedTips = new HashSet<>();
            try {
                Hash tip = entryPoint(preferableMilestone, extraTip, depth);

                serialUpdateRatings(tip, ratings, analyzedTips, extraTip);
                analyzedTips.clear();

                return randomWalk(tip, extraTip, ratings, seed);

            } catch (Exception e) {
                e.printStackTrace();
                log.error("Encountered error: " + e.getLocalizedMessage());
            } finally {
                API.incEllapsedTime_getTxToApprove(System.nanoTime() - startTime);
            }
        }
        return null;
    }

    Hash monteCarloTransactionToApprove(final Hash reference, final Hash extraTip, final int depth, final int iterations, Random seed) {

        long startTime = System.nanoTime();

        if(milestone.latestSolidSubtangleMilestoneIndex > Milestone.MILESTONE_START_INDEX ||
                milestone.latestMilestoneIndex == Milestone.MILESTONE_START_INDEX) {

            Map<Hash, Long> ratings = new HashMap<>();
            Set<Hash> analyzedTips = new HashSet<>();
            try {
                Hash tip = entryPoint(reference, extraTip, depth);
                serialUpdateRatings(tip, ratings, analyzedTips, extraTip);
                analyzedTips.clear();
                return markovChainMonteCarlo(tip, extraTip, ratings, iterations, seed);
            } catch (Exception e) {
                e.printStackTrace();
                log.error("Encountered error: " + e.getLocalizedMessage());
            } finally {
                API.incEllapsedTime_getTxToApprove(System.nanoTime() - startTime);
            }
        }
        return null;
    }

    Hash entryPoint(final Hash reference, final Hash extraTip, final int depth) throws Exception {
        Hash tip = reference == null ? milestone.latestSolidSubtangleMilestone : reference;
        if (extraTip != null) {
            int depositIndex = TransactionViewModel.fromHash(tangle, reference).snapshotIndex();
            int milestoneIndex = (depositIndex > 0 ? depositIndex : milestone.latestSolidSubtangleMilestoneIndex) - depth;
            if(milestoneIndex < 0) {
                milestoneIndex = 0;
            }
            MilestoneViewModel milestoneViewModel = MilestoneViewModel.findClosestNextMilestone(tangle, milestoneIndex);
            if(milestoneViewModel != null && milestoneViewModel.getHash() != null) {
                tip = milestoneViewModel.getHash();
            }
        }
        return tip;
    }

    Hash markovChainMonteCarlo(final Hash tip, final Hash extraTip, final Map<Hash, Long> ratings, final int iterations, final Random seed) throws Exception {
        Map<Hash, Integer> monteCarloIntegrations = new HashMap<>();
        Hash tail;
        for(int i = iterations; i-- > 0; ) {
            tail = randomWalk(tip, extraTip, ratings, seed);
            if(monteCarloIntegrations.containsKey(tail)) {
                monteCarloIntegrations.put(tail, monteCarloIntegrations.get(tail) + 1);
            } else {
                monteCarloIntegrations.put(tail,1);
            }
        }
        return monteCarloIntegrations.entrySet().stream().reduce((a, b) -> a.getValue() > b.getValue() ? a : b).map(Map.Entry::getKey).orElse(null);
    }

    Hash randomWalk(final Hash start, final Hash extraTip, final Map<Hash, Long> ratings, Random rnd) throws Exception {
        Hash tip = start, tail = tip;
        Hash[] tips;
        Set<Hash> tipSet;
        Set<Hash> analyzedTips = new HashSet<>();
        int traversedTails = 0;
        TransactionViewModel transactionViewModel;
        int approverIndex;
        double ratingWeight;
        while (tip != null) {
            tipSet = TransactionViewModel.fromHash(tangle, tip).getApprovers(tangle).getHashes();
            tips = tipSet.toArray(new Hash[tipSet.size()]);
            if (tips.length == 0) {
                log.info("Reason to stop: TransactionViewModel is a tip");
                break;
            }
            if (!ratings.containsKey(tip)) {
                serialUpdateRatings(tip, ratings, analyzedTips, extraTip);
                analyzedTips.clear();
            }

            ratingWeight = rnd.nextDouble() * Math.sqrt(ratings.get(tip));
            for (approverIndex = tips.length; approverIndex-- > 1; ) {
                if (ratings.containsKey(tips[approverIndex])) {
                    ratingWeight -= Math.sqrt(ratings.get(tips[approverIndex]));
                }
                if (ratingWeight <= 0) {
                    break;
                }
            }
            transactionViewModel = TransactionViewModel.fromHash(tangle, tips[approverIndex]);
            if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
                log.info("Reason to stop: transactionViewModel == null");
                break;
            } else if (!transactionValidator.checkSolidity(transactionViewModel.getHash(), false)) {
                //} else if (!transactionViewModel.isSolid()) {
                log.info("Reason to stop: !checkSolidity");
                break;
            } else if (!ledgerValidator.updateFromSnapshot(transactionViewModel.getHash())) {
                log.info("Reason to stop: !LedgerValidator");
                break;
            } else if (transactionViewModel.getHash().equals(tip)) {
                log.info("Reason to stop: transactionViewModel==extraTip");
                break;
            } else if (transactionViewModel.getHash().equals(tip)) {
                log.info("Reason to stop: transactionViewModel==itself");
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
    }

    static long capSum(long a, long b, long max) {
        if(a + b < 0 || a + b > max) {
            return max;
        }
        return a+b;
    }

    void serialUpdateRatings(final Hash txHash, final Map<Hash, Long> ratings, final Set<Hash> analyzedTips, final Hash extraTip) throws Exception {
        Stack<Hash> hashesToRate = new Stack<>();
        hashesToRate.push(txHash);
        Hash currentHash;
        boolean addedBack;
        while(!hashesToRate.empty()) {
            currentHash = hashesToRate.pop();
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, currentHash);
            addedBack = false;
            Set<Hash> approvers = transactionViewModel.getApprovers(tangle).getHashes();
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
                long rating = (extraTip != null && ledgerValidator.isApproved(currentHash)? 0: 1) + approvers.stream().map(ratings::get).filter(Objects::nonNull)
                        .reduce((a, b) -> capSum(a,b, Long.MAX_VALUE/2)).orElse(0L);
                ratings.put(currentHash, rating);
            }
        }
    }

    Set<Hash> updateHashRatings(Hash txHash, Map<Hash, Set<Hash>> ratings, Set<Hash> analyzedTips) throws Exception {
        Set<Hash> rating;
        if(analyzedTips.add(txHash)) {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, txHash);
            rating = new HashSet<>(Collections.singleton(txHash));
            Set<Hash> approverHashes = transactionViewModel.getApprovers(tangle).getHashes();
            for(Hash approver : approverHashes) {
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

    long recursiveUpdateRatings(Hash txHash, Map<Hash, Long> ratings, Set<Hash> analyzedTips) throws Exception {
        long rating = 1;
        if(analyzedTips.add(txHash)) {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, txHash);
            Set<Hash> approverHashes = transactionViewModel.getApprovers(tangle).getHashes();
            for(Hash approver : approverHashes) {
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

}
