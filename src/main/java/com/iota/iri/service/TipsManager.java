package com.iota.iri.service;

import java.util.*;

import com.iota.iri.LedgerValidator;
import com.iota.iri.Milestone;
import com.iota.iri.TransactionValidator;
import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.collections.BoundedSetValuedHashMap;
import com.iota.iri.zmq.MessageQ;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.multimap.AbstractSetValuedMap;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TipsManager {

    public static final int MAX_ANCESTORS_SIZE = 10000;

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

            Set<Hash> analyzedTips = new HashSet<>();
            Set<Hash> maxDepthOk = new HashSet<>();
            try {
                Hash tip = entryPoint(reference, extraTip, depth);
                Map<Hash, Integer> cumulativeWeights = calculateCumulativeWeight(visitedHashes, tip,
                        extraTip != null, new HashSet<>());
                analyzedTips.clear();
                if (ledgerValidator.updateDiff(visitedHashes, diff, tip)) {
                    return markovChainMonteCarlo(visitedHashes, diff, tip, extraTip, cumulativeWeights, iterations, milestone.latestSolidSubtangleMilestoneIndex - depth * 2, maxDepthOk, seed);
                } else {
                    throw new RuntimeException("starting tip failed consistency check: " + tip.toString());
                }
            } catch (Exception e) {
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

    Hash markovChainMonteCarlo(final Set<Hash> visitedHashes, final Map<Hash, Long> diff, Hash tip, Hash extraTip, Map<Hash, Integer> cumulativeWeight,
            int iterations, int maxDepth, Set<Hash> maxDepthOk, Random seed) throws Exception {
        Map<Hash, Integer> monteCarloIntegrations = new HashMap<>();
        Hash tail;
        for(int i = iterations; i-- > 0; ) {
            tail = randomWalk(visitedHashes, diff, tip, extraTip, cumulativeWeight,
                    maxDepth, maxDepthOk, seed);
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

    /**
     * Performs a walk from {@code start} until you reach a tip or {@code extraTip}. The path depends of the values
     * of transaction weights given in {@code cumulativeWeights}. If a tx weight is missing, then calculate it on
     * the fly.
     *
     * @param visitedHashes hashes of transactions that were validated and their weights can be disregarded when we have
     *                      {@code extraTip} is not {@code null}.
     * @param diff map of address to change in balance since last snapshot.
     * @param start hash of the transaction that starts the walk.
     * @param extraTip an extra ending point for the walk. If not null the walk will ignore the weights of
     * {@code visitedHashes}.
     * @param cumulativeWeights maps transaction hashes to weights. Missing data is computed by this method.
     * @param maxDepth the transactions we are traversing may not be below this depth measured in number of snapshots.
     * @param maxDepthOk transaction hashes that we know are not below {@code maxDepth}
     * @param rnd generates random doubles to make the walk less deterministic
     * @return a tip's hash
     * @throws Exception
     */
    Hash randomWalk(final Set<Hash> visitedHashes, final Map<Hash, Long> diff, final Hash start, final Hash extraTip, final Map<Hash, Integer> cumulativeWeights, final int maxDepth, final Set<Hash> maxDepthOk, Random rnd) throws Exception {
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
                if (!cumulativeWeights.containsKey(tip)) {
                    cumulativeWeights.putAll(calculateCumulativeWeight(myApprovedHashes, tip, extraTip != null,
                            analyzedTips));
                    analyzedTips.clear();
                }

                walkRatings = new double[tips.length];
                double maxRating = 0;
                long tipRating = cumulativeWeights.get(tip);
                for (int i = 0; i < tips.length; i++) {
                    //transition probability = ((Hx-Hy)^-3)/maxRating
                    walkRatings[i] = Math.pow(tipRating - cumulativeWeights.getOrDefault(tips[i],0), -3);
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

    /**
     * Updates the cumulative weight of txs.
     * A cumulative weight of each tx is 1 + the number of ancestors it has.
     *
     * See https://github.com/alongalky/iota-docs/blob/master/cumulative.md
     *
     *
     * @param myApprovedHashes the current hashes of the snapshot at the time of calculation
     * @param currentTxHash the transaction from where the analysis starts
     * @param confirmLeftBehind if true attempt to give more weight to previously
     *                          unconfirmed txs
     * @throws Exception if there is a problem accessing the db
     */
    Map<Hash, Integer> calculateCumulativeWeight(Set<Hash> myApprovedHashes, Hash currentTxHash, boolean confirmLeftBehind,
            Set<Hash> analyzedTips) throws Exception {
        Collection<TransactionViewModel> txsToRate = sortTransactionsInTopologicalOrder(currentTxHash);
        return calculateCwInOrder(txsToRate, myApprovedHashes, confirmLeftBehind, analyzedTips);
    }

    private Set<TransactionViewModel> sortTransactionsInTopologicalOrder(Hash startTx) throws Exception {
        Set<TransactionViewModel> sortedTxs = new LinkedHashSet<>();
        Set<TransactionViewModel> temporary = new HashSet<>();
        Deque<TransactionViewModel> stack = new ArrayDeque<>();
        Map<TransactionViewModel, Collection<TransactionViewModel>> txToDirectApprovers = new HashMap<>();

        stack.push(TransactionViewModel.fromHash(tangle, startTx));
        while (CollectionUtils.isNotEmpty(stack)) {
            TransactionViewModel tx = stack.peek();
            if (!sortedTxs.contains(tx)) {
                Collection<TransactionViewModel> appHashes = getTxDirectApproversHashes(tx, txToDirectApprovers);
                if (CollectionUtils.isNotEmpty(appHashes)) {
                    TransactionViewModel txApp = getAndRemoveApprover(appHashes);
                    if (!temporary.add(txApp)) {
                        throw new IllegalStateException("A circle was found in a subtangle on hash: " + txApp.getHash());
                    }
                    stack.push(txApp);
                    continue;
                }
            }
            else {
                temporary.remove(stack.pop());
                continue;
            }
            sortedTxs.add(tx);
        }

        return sortedTxs;
    }

    private TransactionViewModel getAndRemoveApprover(Collection<TransactionViewModel> appHashes) {
        Iterator<TransactionViewModel> hashIterator = appHashes.iterator();
        TransactionViewModel txApp = hashIterator.next();
        hashIterator.remove();
        return txApp;
    }

    private Collection<TransactionViewModel> getTxDirectApproversHashes(TransactionViewModel tx,
            Map<TransactionViewModel, Collection<TransactionViewModel>> txToDirectApprovers) throws Exception {
        Collection<TransactionViewModel> txApprovers = txToDirectApprovers.get(tx);
        if (txApprovers == null) {
            ApproveeViewModel approvers = tx.getApprovers(tangle);
            Collection<Hash> appHashes = CollectionUtils.emptyIfNull(approvers.getHashes());
            txApprovers = new HashSet<>(appHashes.size());
            for (Hash appHash : appHashes) {
                //if not genesis (the tx that confirms itself)
                if (ObjectUtils.notEqual(Hash.NULL_HASH, appHash)) {
                    TransactionViewModel txApp = TransactionViewModel.fromHash(tangle, appHash);
                    txApprovers.add(txApp);
                }
            }
            txToDirectApprovers.put(tx, txApprovers);
        }
        return txApprovers;
    }

    private Map<Hash, Integer> calculateCwInOrder(Collection<TransactionViewModel> txsToRate,
            Set<Hash> myApprovedHashes, boolean confirmLeftBehind, Set<Hash> analyzedTips) throws Exception {
        AbstractSetValuedMap<TransactionViewModel, TransactionViewModel> txToApprovers =
                new BoundedSetValuedHashMap<>(MAX_ANCESTORS_SIZE);
        HashMap<Hash, Integer> txToCumulativeWeight = new HashMap<>();

        for (TransactionViewModel transactionViewModel : txsToRate) {
            if (analyzedTips.add(transactionViewModel.getHash())) {
                txToCumulativeWeight = updateCw(txToApprovers, txToCumulativeWeight, transactionViewModel, myApprovedHashes,
                        confirmLeftBehind);
            }
            txToApprovers = updateApproversAndReleaseMemory(txToApprovers, transactionViewModel, myApprovedHashes,
                    confirmLeftBehind);
        }

        return txToCumulativeWeight;
    }


    private AbstractSetValuedMap<TransactionViewModel, TransactionViewModel> updateApproversAndReleaseMemory(
            AbstractSetValuedMap<TransactionViewModel, TransactionViewModel> txToApprovers,
            TransactionViewModel transactionViewModel, Set<Hash> myApprovedHashes, boolean confirmLeftBehind) throws Exception {
        Set<TransactionViewModel> approvers = txToApprovers.get(transactionViewModel);

        TransactionViewModel trunkTransaction = transactionViewModel.getTrunkTransaction(tangle);
        txToApprovers.putAll(trunkTransaction, approvers);
        TransactionViewModel branchTransaction = transactionViewModel.getBranchTransaction(tangle);
        txToApprovers.putAll(branchTransaction, approvers);
        if (shouldIncludeTransaction(transactionViewModel, myApprovedHashes, confirmLeftBehind)) {
            txToApprovers.put(trunkTransaction, transactionViewModel);
            txToApprovers.put(branchTransaction, transactionViewModel);
        }

        txToApprovers.remove(transactionViewModel);

        return txToApprovers;
    }

    private static boolean shouldIncludeTransaction(TransactionViewModel tx, Set<Hash> myApprovedHashes,
            boolean confirmLeftBehind) {
        return tx != null
                && !(confirmLeftBehind && myApprovedHashes.contains(tx.getHash()));
    }

    private HashMap<Hash, Integer> updateCw(AbstractSetValuedMap<TransactionViewModel, TransactionViewModel> txToApprovers,
            HashMap<Hash, Integer> txToCumulativeWeight, TransactionViewModel transactionViewModel,
            Set<Hash> myApprovedHashes, boolean confirmLeftBehind) {
        Set<TransactionViewModel> approvers = txToApprovers.get(transactionViewModel);
        int weight = CollectionUtils.emptyIfNull(approvers).size();
        if (shouldIncludeTransaction(transactionViewModel, myApprovedHashes, confirmLeftBehind)) {
            ++weight;
        }
        txToCumulativeWeight.put(transactionViewModel.getHash(), weight);
        return txToCumulativeWeight;
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
