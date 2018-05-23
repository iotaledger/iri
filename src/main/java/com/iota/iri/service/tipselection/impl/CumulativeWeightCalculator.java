package com.iota.iri.service.tipselection.impl;

import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashId;
import com.iota.iri.model.HashPrefix;
import com.iota.iri.service.tipselection.impl.collections.TransformingBoundedHashSet;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.SafeUtils;
import com.iota.iri.utils.collections.impl.KeyOptimizedMap;
import com.iota.iri.utils.collections.impl.BoundedHashSet;
import com.iota.iri.utils.collections.interfaces.BoundedCollection;
import com.iota.iri.utils.collections.interfaces.BoundedSet;
import com.iota.iri.utils.collections.interfaces.TransformingMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class CumulativeWeightCalculator {

    private static final Logger log = LoggerFactory.getLogger(CumulativeWeightCalculator.class);

    public static final int SUBHASH_LENGTH = 16;
    public static final int MAX_ANCESTORS_SIZE = 1000;

    public final Tangle tangle;
    private static final Function<Hash, Buffer> HASH_TO_PREFIX = (hash) -> {
        if (hash == null) {
            return null;
        }
        return ByteBuffer.wrap(Arrays.copyOf(hash.bytes(), SUBHASH_LENGTH));
    };


    public CumulativeWeightCalculator(Tangle tangle) {
        this.tangle = tangle;
    }

    //See https://github.com/alongalky/iota-docs/blob/master/cumulative.md
    TransformingMap<HashId, Integer> calculate(Hash entryPoint) throws Exception {
        log.info("Start calculating cw starting with tx hash {}", entryPoint);

        LinkedHashSet<Hash> txHashesToRate = sortTransactionsInTopologicalOrder(entryPoint);
        TransformingMap<HashId, Integer> cumulativeWeights = calculateCwInOrder(txHashesToRate);
        return cumulativeWeights;
    }

    //Uses DFS algorithm to sort
    private LinkedHashSet<Hash> sortTransactionsInTopologicalOrder(Hash startTx) throws Exception {
        LinkedHashSet<Hash> sortedTxs = new LinkedHashSet<>();
        Set<Hash> temporary = new HashSet<>();
        Deque<Hash> stack = new ArrayDeque<>();
        Map<Hash, Collection<Hash>> txToDirectApprovers = new HashMap<>();

        stack.push(startTx);
        while (CollectionUtils.isNotEmpty(stack)) {
            Hash txHash = stack.peek();
            if (!sortedTxs.contains(txHash)) {
                Collection<Hash> appHashes = getTxDirectApproversHashes(txHash, txToDirectApprovers);
                if (CollectionUtils.isNotEmpty(appHashes)) {
                    Hash txApp = getAndRemoveApprover(appHashes);
                    if (!temporary.add(txApp)) {
                        throw new IllegalStateException("A circle or a collision was found in a subtangle on hash: "
                                + txApp);
                    }
                    stack.push(txApp);
                    continue;
                }
            }
            else {
                txHash = stack.pop();
                temporary.remove(txHash);
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

    private Collection<Hash> getTxDirectApproversHashes(Hash txHash,
                                                        Map<Hash, Collection<Hash>> txToDirectApprovers) throws Exception {
        Collection<Hash> txApprovers = txToDirectApprovers.get(txHash);
        if (txApprovers == null) {
            ApproveeViewModel approvers = ApproveeViewModel.load(tangle, txHash);
            Collection<Hash> appHashes = CollectionUtils.emptyIfNull(approvers.getHashes());
            txApprovers = new HashSet<>(appHashes.size());
            for (Hash appHash : appHashes) {
                //if not genesis (the tx that confirms itself)
                if (ObjectUtils.notEqual(Hash.NULL_HASH, appHash)) {
                    txApprovers.add(appHash);
                }
            }
            txToDirectApprovers.put(txHash, txApprovers);
        }
        return txApprovers;
    }

    //must specify using LinkedHashSet since Java has no interface that guarantees uniqueness and insertion order
    private TransformingMap<HashId, Integer> calculateCwInOrder(LinkedHashSet<Hash> txsToRate) throws Exception {
        TransformingMap<HashId, Set<HashId>> txToApproversPrefix = createTxToApproversPrefixMap();
        TransformingMap<HashId, Integer> txHashToCumulativeWeight = createTxHashToCumulativeWeightTask(txsToRate.size());

        Iterator<Hash> txHashIterator = txsToRate.iterator();
        while (txHashIterator.hasNext()) {
            Hash txHash = txHashIterator.next();
            txHashToCumulativeWeight = updateCw(txToApproversPrefix, txHashToCumulativeWeight, txHash);
            txToApproversPrefix = updateApproversAndReleaseMemory(txToApproversPrefix, txHash);
            txHashIterator.remove();
        }
        return txHashToCumulativeWeight;
    }


    private <T extends HashId> TransformingMap<HashId, Set<HashId>> updateApproversAndReleaseMemory(TransformingMap<HashId,
            Set<HashId>> txHashToApprovers, Hash txHash) throws Exception {
        Set<HashId> approvers = SetUtils.emptyIfNull(txHashToApprovers.get(txHash));

        TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, txHash);
        Hash trunkHash = transactionViewModel.getTrunkTransactionHash();
        Hash branchHash = transactionViewModel.getBranchTransactionHash();
        Set<HashId> trunkApprovers = createTransformingBoundedSet(approvers);
        trunkApprovers.addAll(CollectionUtils.emptyIfNull(txHashToApprovers.get(trunkHash)));
        trunkApprovers.add(txHash);
        txHashToApprovers.put(trunkHash, trunkApprovers);

        Set<HashId> branchApprovers = createTransformingBoundedSet(approvers);
        branchApprovers.addAll(CollectionUtils.emptyIfNull(txHashToApprovers.get(branchHash)));
        branchApprovers.add(txHash);
        txHashToApprovers.put(branchHash, branchApprovers);

        txHashToApprovers.remove(txHash);

        return txHashToApprovers;
    }

    private static <T extends HashId> TransformingMap<HashId, Integer> updateCw(TransformingMap<HashId, Set<T>> txHashToApprovers,
                                                           TransformingMap<HashId, Integer> txToCumulativeWeight, Hash txHash) {
        Set<T> approvers = txHashToApprovers.get(txHash);
        int weight = CollectionUtils.emptyIfNull(approvers).size() + 1;
        txToCumulativeWeight.put(txHash, weight);
        return txToCumulativeWeight;
    }

    private static TransformingMap<HashId, Set<HashId>> createTxToApproversPrefixMap() {
       return new KeyOptimizedMap<>(HashPrefix::createPrefix, null);
    }

    private static TransformingMap<HashId, Integer> createTxHashToCumulativeWeightTask(int size) {
        return new KeyOptimizedMap<>(size, HashPrefix::createPrefix, null);
    }

    private static  BoundedSet<HashId> createTransformingBoundedSet(Collection<HashId> c) {
        return new TransformingBoundedHashSet<>(c, MAX_ANCESTORS_SIZE, HashPrefix::createPrefix);
    }



}
