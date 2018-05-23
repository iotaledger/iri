package com.iota.iri.service.tipselection.impl;

import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.collections.impl.KeyOptimizedMap;
import com.iota.iri.utils.collections.impl.BoundedHashSet;
import com.iota.iri.utils.collections.interfaces.BoundedSet;
import com.iota.iri.utils.collections.interfaces.OptimizedMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Function;

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
    OptimizedMap<Hash, Integer> calculate(Hash entryPoint) throws Exception {
        log.info("Start calculating cw starting with tx hash {}", entryPoint);
        log.debug("Start topological sort");
        long start = System.currentTimeMillis();
        LinkedHashSet<Hash> txHashesToRate = sortTransactionsInTopologicalOrder(entryPoint);
        log.debug("Subtangle size: {}", txHashesToRate.size());
        log.debug("Topological sort done. Start traversing on txs in order and calculate weight");
        OptimizedMap<Hash, Integer> cumulativeWeights = calculateCwInOrder(txHashesToRate);
        if (log.isDebugEnabled()) {
            log.debug("Cumulative weights calculation done in {} ms", System.currentTimeMillis() - start);
        }
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
    private OptimizedMap<Hash, Integer> calculateCwInOrder(LinkedHashSet<Hash> txsToRate) throws Exception {
        OptimizedMap<Hash, Set<Buffer>> txToApproversPrefix = new KeyOptimizedMap<>(HASH_TO_PREFIX);
        OptimizedMap<Hash, Integer> txHashToCumulativeWeight = new KeyOptimizedMap<>(txsToRate.size(),
                HASH_TO_PREFIX);

        Iterator<Hash> txHashIterator = txsToRate.iterator();
        while (txHashIterator.hasNext()) {
            Hash txHash = txHashIterator.next();
            txHashToCumulativeWeight = updateCw(txToApproversPrefix, txHashToCumulativeWeight, txHash);
            txToApproversPrefix = updateApproversAndReleaseMemory(txToApproversPrefix, txHash);
            txHashIterator.remove();
        }
        return txHashToCumulativeWeight;
    }


    private OptimizedMap<Hash, Set<Buffer>> updateApproversAndReleaseMemory(OptimizedMap<Hash, Set<Buffer>> txHashToApprovers,
                                                                     Hash txHash) throws Exception {
        BoundedSet<Buffer> approvers =
                new BoundedHashSet<>(SetUtils.emptyIfNull(txHashToApprovers.get(txHash)), MAX_ANCESTORS_SIZE);

        TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, txHash);
        Hash trunkHash = transactionViewModel.getTrunkTransactionHash();
        Hash branchHash = transactionViewModel.getBranchTransactionHash();
        if (!approvers.isFull()) {
            Buffer hashPrefix = HASH_TO_PREFIX.apply(txHash);

            Set<Buffer> trunkApprovers = new BoundedHashSet<>(approvers, MAX_ANCESTORS_SIZE);
            trunkApprovers.addAll(CollectionUtils.emptyIfNull(txHashToApprovers.get(trunkHash)));
            trunkApprovers.add(hashPrefix);
            txHashToApprovers.put(trunkHash, trunkApprovers);

            Set<Buffer> branchApprovers = new BoundedHashSet<>(approvers, MAX_ANCESTORS_SIZE);
            branchApprovers.addAll(CollectionUtils.emptyIfNull(txHashToApprovers.get(branchHash)));
            branchApprovers.add(hashPrefix);
            txHashToApprovers.put(branchHash, branchApprovers);
        }
        else {
            txHashToApprovers.put(trunkHash, approvers);
            txHashToApprovers.put(branchHash, approvers);
        }
        txHashToApprovers.remove(txHash);

        return txHashToApprovers;
    }

    private static OptimizedMap<Hash, Integer> updateCw(OptimizedMap<Hash, Set<Buffer>> txHashToApprovers,
                                          OptimizedMap<Hash, Integer> txToCumulativeWeight, Hash txHash) {
        Set<Buffer> approvers = txHashToApprovers.get(txHash);
        int weight = CollectionUtils.emptyIfNull(approvers).size() + 1;
        txToCumulativeWeight.put(txHash, weight);
        return txToCumulativeWeight;
    }
}
