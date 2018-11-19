package com.iota.iri.service.tipselection.impl;

import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashId;
import com.iota.iri.model.HashPrefix;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.tipselection.RatingCalculator;
import com.iota.iri.utils.collections.impl.TransformingBoundedHashSet;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.collections.impl.TransformingMap;
import com.iota.iri.utils.collections.interfaces.BoundedSet;
import com.iota.iri.utils.collections.interfaces.UnIterableMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Implementation of <tt>RatingCalculator</tt> that gives the cumulative for each transaction referencing entryPoint.
 * Used to create a weighted random walks.
 *
 * @see <a href="cumulative.md">https://github.com/alongalky/iota-docs/blob/master/cumulative.md</a>
 */
public class CumulativeWeightCalculator implements RatingCalculator{

    private static final Logger log = LoggerFactory.getLogger(CumulativeWeightCalculator.class);
    public static final int MAX_FUTURE_SET_SIZE = 5000;

    public final Tangle tangle;
    private final SnapshotProvider snapshotProvider;

    public CumulativeWeightCalculator(Tangle tangle, SnapshotProvider snapshotProvider) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
    }

    @Override
    public UnIterableMap<HashId, Integer> calculate(Hash entryPoint) throws Exception {
        log.debug("Start calculating cw starting with tx hash {}", entryPoint);

        LinkedHashSet<Hash> txHashesToRate = sortTransactionsInTopologicalOrder(entryPoint);
        return calculateCwInOrder(txHashesToRate);
    }

    //Uses DFS algorithm to sort
    private LinkedHashSet<Hash> sortTransactionsInTopologicalOrder(Hash startTx) throws Exception {
        LinkedHashSet<Hash> sortedTxs = new LinkedHashSet<>();
        Deque<Hash> stack = new ArrayDeque<>();
        Map<Hash, Collection<Hash>> txToDirectApprovers = new HashMap<>();

        stack.push(startTx);
        while (CollectionUtils.isNotEmpty(stack)) {
            Hash txHash = stack.peek();
            if (!sortedTxs.contains(txHash)) {
                Collection<Hash> appHashes = getTxDirectApproversHashes(txHash, txToDirectApprovers);
                if (CollectionUtils.isNotEmpty(appHashes)) {
                    Hash txApp = getAndRemoveApprover(appHashes);
                    stack.push(txApp);
                    continue;
                }
            }
            else {
                stack.pop();
                continue;
            }
            sortedTxs.add(txHash);
        }

        return sortedTxs;
    }

    private Hash getAndRemoveApprover(Collection<Hash> appHashes) {
        Iterator<Hash> hashIterator = appHashes.iterator();
        Hash txApp = hashIterator.next();
        hashIterator.remove();
        return txApp;
    }

    private Collection<Hash> getTxDirectApproversHashes(Hash txHash, Map<Hash, Collection<Hash>> txToDirectApprovers)
            throws Exception {
        Collection<Hash> txApprovers = txToDirectApprovers.get(txHash);
        if (txApprovers == null) {
            ApproveeViewModel approvers = ApproveeViewModel.load(tangle, txHash);
            Collection<Hash> appHashes = CollectionUtils.emptyIfNull(approvers.getHashes());
            txApprovers = new HashSet<>(appHashes.size());
            for (Hash appHash : appHashes) {
                //if not genesis (the tx that confirms itself)
                if (!snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(appHash)) {
                    txApprovers.add(appHash);
                }
            }
            txToDirectApprovers.put(txHash, txApprovers);
        }
        return txApprovers;
    }

    //must specify using LinkedHashSet since Java has no interface that guarantees uniqueness and insertion order
    private UnIterableMap<HashId, Integer> calculateCwInOrder(LinkedHashSet<Hash> txsToRate) throws Exception {
        UnIterableMap<HashId, Set<HashId>> txHashToApprovers = createTxHashToApproversPrefixMap();
        UnIterableMap<HashId, Integer> txHashToCumulativeWeight = createTxHashToCumulativeWeightMap(txsToRate.size());

        Iterator<Hash> txHashIterator = txsToRate.iterator();
        while (txHashIterator.hasNext()) {
            Hash txHash = txHashIterator.next();
            txHashToCumulativeWeight = updateCw(txHashToApprovers, txHashToCumulativeWeight, txHash);
            txHashToApprovers = updateApproversAndReleaseMemory(txHashToApprovers, txHash);
            txHashIterator.remove();
        }
        return txHashToCumulativeWeight;
    }


    private UnIterableMap<HashId, Set<HashId>> updateApproversAndReleaseMemory(UnIterableMap<HashId,
                    Set<HashId>> txHashToApprovers, Hash txHash) throws Exception {
        Set<HashId> approvers = SetUtils.emptyIfNull(txHashToApprovers.get(txHash));

        TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, txHash);
        Hash trunkHash = transactionViewModel.getTrunkTransactionHash();
        Hash branchHash = transactionViewModel.getBranchTransactionHash();

        Set<HashId> trunkApprovers = createApprovers(txHashToApprovers, txHash, approvers, trunkHash);
        txHashToApprovers.put(trunkHash, trunkApprovers);
        Set<HashId> branchApprovers = createApprovers(txHashToApprovers, txHash, approvers, branchHash);
        txHashToApprovers.put(branchHash, branchApprovers);

        txHashToApprovers.remove(txHash);

        return txHashToApprovers;
    }

    private Set<HashId> createApprovers(UnIterableMap<HashId, Set<HashId>> txHashToApprovers, HashId txHash,
                                        Set<HashId> approvers, HashId trunkHash) {
        Set<HashId> approverSet = createTransformingBoundedSet(approvers);
        approverSet.addAll(CollectionUtils.emptyIfNull(txHashToApprovers.get(trunkHash)));
        approverSet.add(txHash);
        return approverSet;
    }

    private static <T extends HashId> UnIterableMap<HashId, Integer> updateCw(
            UnIterableMap<HashId, Set<T>> txHashToApprovers, UnIterableMap<HashId, Integer> txToCumulativeWeight,
            Hash txHash) {
        Set<T> approvers = txHashToApprovers.get(txHash);
        int weight = CollectionUtils.emptyIfNull(approvers).size() + 1;
        txToCumulativeWeight.put(txHash, weight);
        return txToCumulativeWeight;
    }

    private static UnIterableMap<HashId, Set<HashId>> createTxHashToApproversPrefixMap() {
       return new TransformingMap<>(HashPrefix::createPrefix, null);
    }

    private static UnIterableMap<HashId, Integer> createTxHashToCumulativeWeightMap(int size) {
        return new TransformingMap<>(size, HashPrefix::createPrefix, null);
    }

    private static  BoundedSet<HashId> createTransformingBoundedSet(Collection<HashId> c) {
        return new TransformingBoundedHashSet<>(c, MAX_FUTURE_SET_SIZE, HashPrefix::createPrefix);
    }
}
