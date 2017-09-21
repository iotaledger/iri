package com.iota.iri.service;

import java.util.*;

import com.iota.iri.LedgerValidator;
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

    Hash transactionToApprove(final Hash reference, final Hash extraTip, final int depth, final int iterations, Random seed) {

        long startTime = System.nanoTime();
        final int msDepth;
        if(depth > maxDepth) {
            msDepth = maxDepth;
        } else {
            msDepth = depth;
        }
        final int maxDepth =  milestone.latestSolidSubtangleMilestoneIndex-depth*2;

        if(milestone.latestSolidSubtangleMilestoneIndex > Milestone.MILESTONE_START_INDEX ||
                milestone.latestMilestoneIndex == Milestone.MILESTONE_START_INDEX) {

            Set<Hash> maxDepthOk = new HashSet<>();
            try {
                Hash tip = reference == null ? milestone.latestSolidSubtangleMilestone : reference;
                // get entrypoint at some depth
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

                Map<Integer, Set<Hash>> monteCarloIntegrations = new HashMap<>();
                Hash tail = tip;
                Hash hash = tail;
                Hash[] tips;
                Set<Hash> tipSet;
                TransactionViewModel transactionViewModel;
                int approverIndex;
                double ratingWeight;
                long[] walkWeights;
                double[] walkRatings;
                long initialCumulativeWeight = getCumulativeWeight(tail);

                // The monte carlo discrete simulation
                for(int i = iterations; i-- > 0; ) {
                    int traversedTails = 0;
                    long tailCumulativeWeight = initialCumulativeWeight;
                    while (hash != null) {
                        transactionViewModel = TransactionViewModel.fromHash(tangle, hash);
                        tipSet = transactionViewModel.getApprovers(tangle).getHashes();
                        if(transactionViewModel.getCurrentIndex() == 0) {
                            traversedTails++;
                            tail = hash;
                        } else {
                            hash = tipSet.iterator().next();
                            continue;
                        }
                        if(tipSet.size() == 0) {
                            log.info("Reason to stop: TransactionViewModel is a tip");
                            messageQ.publish("rtst %s", hash);
                            break;
                        }
                        tips = tipSet.toArray(new Hash[tipSet.size()]);
                        tipSet.clear();

                        walkWeights = new long[tips.length];
                        walkRatings = new double[tips.length];

                        double sumDeltaWeight = 0;
                        for(approverIndex = tips.length; approverIndex-- > 1; ) {
                            walkWeights[approverIndex] = getCumulativeWeight(tips[approverIndex]);
                            walkRatings[approverIndex] = Math.pow(tailCumulativeWeight - walkWeights[approverIndex], -3);
                            sumDeltaWeight += walkRatings[approverIndex];
                        }
                        for(approverIndex = tips.length; approverIndex-- > 1; ) {
                            walkRatings[approverIndex] /= sumDeltaWeight;
                        }
                        ratingWeight = seed.nextDouble();
                        for (approverIndex = tips.length; approverIndex-- > 1; ) {
                            ratingWeight -= walkRatings[approverIndex];
                            if (ratingWeight <= 0) {
                                break;
                            }
                        }

                        tailCumulativeWeight = walkWeights[approverIndex];
                        transactionViewModel = TransactionViewModel.fromHash(tangle, tips[approverIndex]);
                        if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
                            log.info("Reason to stop: transactionViewModel == null");
                            messageQ.publish("rtsn %s", transactionViewModel.getHash());
                            break;
                        } else if (!transactionValidator.checkSolidity(transactionViewModel.getHash(), false)) {
                            //} else if (!transactionViewModel.isSolid()) {
                            log.info("Reason to stop: !checkSolidity");
                            messageQ.publish("rtss %s", transactionViewModel.getHash());
                            break;

                        } else if (belowMaxDepth(transactionViewModel.getHash(), maxDepth, maxDepthOk)) {
                            log.info("Reason to stop: belowMaxDepth");
                            break;

                        } else if (!ledgerValidator.updateFromSnapshot(transactionViewModel.getHash())) {
                            log.info("Reason to stop: !LedgerValidator");
                            messageQ.publish("rtsv %s", transactionViewModel.getHash());
                            break;
                        } else if (transactionViewModel.getHash().equals(extraTip)) {
                            log.info("Reason to stop: transactionViewModel==extraTip");
                            messageQ.publish("rtsd %s", transactionViewModel.getHash());
                            break;
                        } else if (transactionViewModel.getHash().equals(hash)) {
                            log.info("Reason to stop: transactionViewModel==itself");
                            messageQ.publish("rtsl %s", transactionViewModel.getHash());
                            break;
                        } else {
                            hash = transactionViewModel.getHash();
                        }
                    }
                    log.info("Tx traversed to find tip: " + traversedTails);
                    messageQ.publish("mctn %d", traversedTails);
                    if(monteCarloIntegrations.containsKey(traversedTails)) {
                        monteCarloIntegrations.get(traversedTails).add(tail);
                    } else {
                        monteCarloIntegrations.put(traversedTails, new HashSet<>(Collections.singleton(tail)));
                    }
                }
                return monteCarloIntegrations.entrySet().stream().reduce((a, b) -> {
                    if (a.getValue().size() > b.getValue().size()) {
                        return a;
                    } else if (a.getValue().size() < b.getValue().size()) {
                        return b;
                    } else if (seed.nextBoolean()) {
                        return a;
                    } else {
                        return b;
                    }
                }).map(Map.Entry::getValue)
                        .orElse(new HashSet<>())
                        .stream()
                        .reduce((a, b) -> seed.nextBoolean() ? a : b)
                        .orElse(Hash.NULL_HASH);
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

    public long getCumulativeWeight(final Hash tip) throws Exception {
        long cumulativeWeight = 0;
        Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
        Set<Hash> analyzedTranscations = new HashSet<>();
        Hash hash;
        TransactionViewModel transactionViewModel;
        while((hash = nonAnalyzedTransactions.poll()) != null) {
            if(analyzedTranscations.add(hash)) {
                transactionViewModel = TransactionViewModel.fromHash(tangle, hash);
                cumulativeWeight++;
                if(cumulativeWeight >= Long.MAX_VALUE/2) {
                    cumulativeWeight = Long.MAX_VALUE/2;
                    break;
                }
                nonAnalyzedTransactions.addAll(transactionViewModel.getApprovers(tangle).getHashes());
            }
        }
        analyzedTranscations.clear();
        return cumulativeWeight;
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
