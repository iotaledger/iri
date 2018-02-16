package com.iota.iri.service;

import java.util.*;

import com.iota.iri.LedgerValidator;
import com.iota.iri.Snapshot;
import com.iota.iri.TransactionValidator;
import com.iota.iri.model.Hash;
import com.iota.iri.controllers.*;
import com.iota.iri.storage.Tangle;
import com.iota.iri.zmq.MessageQ;
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
    private final MessageQ messageQ;

    private int RATING_THRESHOLD = 75; // Must be in [0..100] range
    private boolean shuttingDown = false;
    private int RESCAN_TX_TO_REQUEST_INTERVAL = 750;
    private final int maxDepth;
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
                       final Milestone milestone,
                       final int maxDepth,
                       final MessageQ messageQ) {
        this.tangle = tangle;
        this.ledgerValidator = ledgerValidator;
        this.transactionValidator = transactionValidator;
        this.tipsViewModel = tipsViewModel;
        this.milestone = milestone;
        this.maxDepth = maxDepth;
        this.messageQ = messageQ;
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
            if(hash != null  && isTip && transactionValidator.checkSolidity(hash, false)) {
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

    Hash transactionToApprove(final Set<Hash> visitedHashes, final Map<Hash, Long> diff, final Hash reference, final Hash extraTip, int depth, final int iterations, Random seed) throws Exception {

        long startTime = System.nanoTime();
        if(depth > maxDepth) {
            depth = maxDepth;
        }

        if(milestone.latestSolidSubtangleMilestoneIndex > Milestone.MILESTONE_START_INDEX ||
                milestone.latestMilestoneIndex == Milestone.MILESTONE_START_INDEX) {

            Map<Hash, Long> ratings = new HashMap<>();
            Set<Hash> analyzedTips = new HashSet<>();
            Set<Hash> maxDepthOk = new HashSet<>();
            try {
                Hash tip = entryPoint(reference, extraTip, depth);
                serialUpdateRatings(visitedHashes, tip, ratings, analyzedTips, extraTip);
                analyzedTips.clear();
                if (ledgerValidator.updateDiff(visitedHashes, diff, tip)) {
                    return markovChainMonteCarlo(visitedHashes, diff, tip, extraTip, ratings, iterations, milestone.latestSolidSubtangleMilestoneIndex - depth * 2, maxDepthOk, seed);
                } else {
                    throw new RuntimeException("starting tip failed consistency check: " + tip.toString());
                }
            } catch (Exception e) {
                milestone.latestSnapshot.rwlock.readLock().unlock();
                e.printStackTrace();
                log.error("Encountered error: " + e.getLocalizedMessage());
                throw e;
            } finally {
                API.incEllapsedTime_getTxToApprove(System.nanoTime() - startTime);
            }
        }
        return null;
    }

    Hash entryPoint(final Hash reference, final Hash extraTip, final int depth) throws Exception {

        if (extraTip == null) {
            //trunk
            return reference != null ? reference : milestone.latestSolidSubtangleMilestone;
        }

        //branch (extraTip)
        int milestoneIndex = Math.max(milestone.latestSolidSubtangleMilestoneIndex - depth - 1, 0);
        MilestoneViewModel milestoneViewModel = MilestoneViewModel.findClosestNextMilestone(tangle, milestoneIndex);
        if(milestoneViewModel != null && milestoneViewModel.getHash() != null) {
            return milestoneViewModel.getHash();
        }

        return milestone.latestSolidSubtangleMilestone;
    }

    Hash markovChainMonteCarlo(final Set<Hash> visitedHashes, final Map<Hash, Long> diff, final Hash tip, final Hash extraTip, final Map<Hash, Long> ratings, final int iterations, final int maxDepth, final Set<Hash> maxDepthOk, final Random seed) throws Exception {
        Map<Hash, Integer> monteCarloIntegrations = new HashMap<>();
        Hash tail;
        for(int i = iterations; i-- > 0; ) {
            tail = randomWalk(visitedHashes, diff, tip, extraTip, ratings, maxDepth, maxDepthOk, seed);
            if(monteCarloIntegrations.containsKey(tail)) {
                monteCarloIntegrations.put(tail, monteCarloIntegrations.get(tail) + 1);
            } else {
                monteCarloIntegrations.put(tail,1);
            }
        }
        return monteCarloIntegrations.entrySet().stream().reduce((a, b) -> {
            if (a.getValue() > b.getValue()) {
                return a;
            } else if (a.getValue() < b.getValue()) {
                return b;
            } else if (seed.nextBoolean()) {
                return a;
            } else {
                return b;
            }
        }).map(Map.Entry::getKey).orElse(null);
    }

    Hash randomWalk(final Set<Hash> visitedHashes, final Map<Hash, Long> diff, final Hash start, final Hash extraTip, final Map<Hash, Long> ratings, final int maxDepth, final Set<Hash> maxDepthOk, Random rnd) throws Exception {
        Hash tip = start, tail = tip;
        Hash[] tips;
        Set<Hash> tipSet;
        Set<Hash> analyzedTips = new HashSet<>();
        int traversedTails = 0;
        TransactionViewModel transactionViewModel;
        int approverIndex;
        double ratingWeight;
        double[] walkRatings;
        List<Hash> extraTipList = null;
        if (extraTip != null) {
            extraTipList = Collections.singletonList(extraTip);
        }
        Map<Hash, Long> myDiff = new HashMap<>(diff);
        Set<Hash> myApprovedHashes = new HashSet<>(visitedHashes);

        while (tip != null) {
            transactionViewModel = TransactionViewModel.fromHash(tangle, tip);
            tipSet = transactionViewModel.getApprovers(tangle).getHashes();
            if(transactionViewModel.getCurrentIndex() == 0) {
                if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
                    log.info("Reason to stop: transactionViewModel == null");
                    messageQ.publish("rtsn %s", transactionViewModel.getHash());
                    break;
                } else if (!transactionValidator.checkSolidity(transactionViewModel.getHash(), false)) {
                    log.info("Reason to stop: !checkSolidity");
                    messageQ.publish("rtss %s", transactionViewModel.getHash());
                    break;
                } else if (belowMaxDepth(transactionViewModel.getHash(), maxDepth, maxDepthOk)) {
                    log.info("Reason to stop: belowMaxDepth");
                    break;
                } else if (!ledgerValidator.updateDiff(myApprovedHashes, myDiff, transactionViewModel.getHash())) {
                    log.info("Reason to stop: !LedgerValidator");
                    messageQ.publish("rtsv %s", transactionViewModel.getHash());
                    break;
                } else if (transactionViewModel.getHash().equals(extraTip)) {
                    log.info("Reason to stop: transactionViewModel==extraTip");
                    messageQ.publish("rtsd %s", transactionViewModel.getHash());
                    break;
                }
                // set the tail here!
                tail = tip;
                traversedTails++;
            }
            if(tipSet.size() == 0) {
                log.info("Reason to stop: TransactionViewModel is a tip");
                messageQ.publish("rtst %s", tip);
                break;
            } else if (tipSet.size() == 1) {
                Iterator<Hash> hashIterator = tipSet.iterator();
                if(hashIterator.hasNext()) {
                    tip = hashIterator.next();
                } else {
                    tip = null;
                }
            } else {
                // walk to the next approver
                tips = tipSet.toArray(new Hash[tipSet.size()]);
                if (!ratings.containsKey(tip)) {
                    serialUpdateRatings(myApprovedHashes, tip, ratings, analyzedTips, extraTip);
                    analyzedTips.clear();
                }

                walkRatings = new double[tips.length];
                double maxRating = 0;
                long tipRating = ratings.get(tip);
                for (int i = 0; i < tips.length; i++) {
                    //transition probability = ((Hx-Hy)^-3)/maxRating
                    walkRatings[i] = Math.pow(tipRating - ratings.getOrDefault(tips[i],0L), -3);
                    maxRating += walkRatings[i];
                }
                ratingWeight = rnd.nextDouble() * maxRating;
                for (approverIndex = tips.length; approverIndex-- > 1; ) {
                    ratingWeight -= walkRatings[approverIndex];
                    if (ratingWeight <= 0) {
                        break;
                    }
                }
                tip = tips[approverIndex];
                if (transactionViewModel.getHash().equals(tip)) {
                    log.info("Reason to stop: transactionViewModel==itself");
                    messageQ.publish("rtsl %s", transactionViewModel.getHash());
                    break;
                }
            }
        }
        log.info("Tx traversed to find tip: " + traversedTails);
        messageQ.publish("mctn %d", traversedTails);
        return tail;
    }

    static long capSum(long a, long b, long max) {
        if(a + b < 0 || a + b > max) {
            return max;
        }
        return a+b;
    }

    void serialUpdateRatings(final Set<Hash> visitedHashes, final Hash txHash, final Map<Hash, Long> ratings, final Set<Hash> analyzedTips, final Hash extraTip) throws Exception {
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
                long rating = (extraTip != null && visitedHashes.contains(currentHash)? 0: 1) + approvers.stream().map(ratings::get).filter(Objects::nonNull)
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

    public int getMaxDepth() {
        return maxDepth;
    }

    boolean belowMaxDepth(Hash tip, int depth, Set<Hash> maxDepthOk) throws Exception {
        //if tip is confirmed stop
        if (TransactionViewModel.fromHash(tangle, tip).snapshotIndex() >= depth) {
            return false;
        }
        //if tip unconfirmed, check if any referenced tx is confirmed below maxDepth
        Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
        Set<Hash> analyzedTranscations = new HashSet<>();
        Hash hash;
        while ((hash = nonAnalyzedTransactions.poll()) != null) {
            if(analyzedTranscations.add(hash)) {
                TransactionViewModel transaction = TransactionViewModel.fromHash(tangle, hash);
                if (transaction.snapshotIndex() != 0 && transaction.snapshotIndex() < depth) {
                    return true;
                }
                if (transaction.snapshotIndex() == 0) {
                    if (maxDepthOk.contains(hash)) {
                        //log.info("Memoization!");
                    } else {
                        nonAnalyzedTransactions.offer(transaction.getTrunkTransactionHash());
                        nonAnalyzedTransactions.offer(transaction.getBranchTransactionHash());
                    }
                }
            }
        }
        maxDepthOk.add(tip);
        return false;
    }
}
