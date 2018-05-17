package com.iota.iri.service.tipselection.impl;

import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.utils.IotaUtils;
import com.iota.iri.utils.SafeUtils;
import com.iota.iri.utils.collections.impl.BoundedHashSet;
import com.iota.iri.utils.collections.interfaces.BoundedSet;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.ObjectUtils;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.*;

public class CumulativeWeightCalculator {

    Map<Hash, Long> calculate(Hash entryPoint) {

    }

    /**
     * Updates the cumulative weight of txs.
     * A cumulative weight of each tx is 1 + the number of ancestors it has.
     * <p>
     * See https://github.com/alongalky/iota-docs/blob/master/cumulative.md
     *
     * @param myApprovedHashes  the current hashes of the snapshot at the time of calculation
     * @param currentTxHash     the transaction from where the analysis starts
     * @param confirmLeftBehind if true attempt to give more weight to previously
     *                          unconfirmed txs
     * @throws Exception if there is a problem accessing the db
     */
    Map<Buffer, Integer> calculateCumulativeWeight(Set<Hash> myApprovedHashes, Hash currentTxHash,
                                                   boolean confirmLeftBehind,
                                                   Set<Hash> analyzedTips) throws Exception {
        log.info("Start calculating cw starting with tx hash {}", currentTxHash);
        log.debug("Start topological sort");
        long start = System.currentTimeMillis();
        LinkedHashSet<Hash> txHashesToRate = sortTransactionsInTopologicalOrder(currentTxHash);
        log.debug("Subtangle size: {}", txHashesToRate.size());
        log.debug("Topological sort done. Start traversing on txs in order and calculate weight");
        Map<Buffer, Integer> cumulativeWeights = calculateCwInOrder(txHashesToRate, myApprovedHashes, confirmLeftBehind,
                analyzedTips);
        log.debug("Cumulative weights calculation done in {} ms", System.currentTimeMillis() - start);
        return cumulativeWeights;
    }

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
            ApproveeViewModel approvers = TransactionViewModel.fromHash(tangle, txHash).getApprovers(tangle);
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
    private Map<Buffer, Integer> calculateCwInOrder(LinkedHashSet<Hash> txsToRate,
                                                    Set<Hash> myApprovedHashes, boolean confirmLeftBehind, Set<Hash> analyzedTips) throws Exception {
        Map<Buffer, Set<Buffer>> txSubHashToApprovers = new HashMap<>();
        Map<Buffer, Integer> txSubHashToCumulativeWeight = new HashMap<>();

        Iterator<Hash> txHashIterator = txsToRate.iterator();
        while (txHashIterator.hasNext()) {
            Hash txHash = txHashIterator.next();
            if (analyzedTips.add(txHash)) {
                txSubHashToCumulativeWeight = updateCw(txSubHashToApprovers, txSubHashToCumulativeWeight, txHash,
                        myApprovedHashes, confirmLeftBehind);
            }
            txSubHashToApprovers = updateApproversAndReleaseMemory(txSubHashToApprovers, txHash, myApprovedHashes,
                    confirmLeftBehind);
            txHashIterator.remove();
        }

        return txSubHashToCumulativeWeight;
    }


    private Map<Buffer, Set<Buffer>> updateApproversAndReleaseMemory(
            Map<Buffer, Set<Buffer>> txSubHashToApprovers,
            Hash txHash, Set<Hash> myApprovedHashes, boolean confirmLeftBehind) throws Exception {
        ByteBuffer txSubHash = IotaUtils.getHashPrefix(txHash, SUBHASH_LENGTH);
        BoundedSet<Buffer> approvers =
                new BoundedHashSet<>(SetUtils.emptyIfNull(txSubHashToApprovers.get(txSubHash)), MAX_ANCESTORS_SIZE);

        if (shouldIncludeTransaction(txHash, myApprovedHashes, confirmLeftBehind)) {
            approvers.add(txSubHash);
        }

        TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, txHash);
        Hash trunkHash = transactionViewModel.getTrunkTransactionHash();
        Buffer trunkSubHash = IotaUtils.getHashPrefix(trunkHash, SUBHASH_LENGTH);
        Hash branchHash = transactionViewModel.getBranchTransactionHash();
        Buffer branchSubHash = IotaUtils.getHashPrefix(branchHash, SUBHASH_LENGTH);
        if (!approvers.isFull()) {
            Set<Buffer> trunkApprovers = new BoundedHashSet<>(approvers, MAX_ANCESTORS_SIZE);
            trunkApprovers.addAll(CollectionUtils.emptyIfNull(txSubHashToApprovers.get(trunkSubHash)));
            Set<Buffer> branchApprovers = new BoundedHashSet<>(approvers, MAX_ANCESTORS_SIZE);
            branchApprovers.addAll(CollectionUtils.emptyIfNull(txSubHashToApprovers.get(branchSubHash)));
            txSubHashToApprovers.put(trunkSubHash, trunkApprovers);
            txSubHashToApprovers.put(branchSubHash, branchApprovers);
        }
        else {
            txSubHashToApprovers.put(trunkSubHash, approvers);
            txSubHashToApprovers.put(branchSubHash, approvers);
        }
        txSubHashToApprovers.remove(txSubHash);

        return txSubHashToApprovers;
    }

    private static boolean shouldIncludeTransaction(Hash txHash, Set<Hash> myApprovedSubHashes,
                                                    boolean confirmLeftBehind) {
        return !confirmLeftBehind || !SafeUtils.isContaining(myApprovedSubHashes, txHash);
    }

    private Map<Buffer, Integer> updateCw(Map<Buffer, Set<Buffer>> txSubHashToApprovers,
                                          Map<Buffer, Integer> txToCumulativeWeight, Hash txHash,
                                          Set<Hash> myApprovedHashes, boolean confirmLeftBehind) {
        ByteBuffer txSubHash = IotaUtils.getHashPrefix(txHash, SUBHASH_LENGTH);
        Set<Buffer> approvers = txSubHashToApprovers.get(txSubHash);
        int weight = CollectionUtils.emptyIfNull(approvers).size();
        if (shouldIncludeTransaction(txHash, myApprovedHashes, confirmLeftBehind)) {
            ++weight;
        }
        txToCumulativeWeight.put(txSubHash, weight);
        return txToCumulativeWeight;
    }
}
