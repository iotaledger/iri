package com.iota.iri.service.tipselection.impl;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;

import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashId;
import com.iota.iri.model.HashPrefix;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.tipselection.RatingCalculator;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.collections.impl.TransformingMap;
import com.iota.iri.utils.collections.interfaces.UnIterableMap;

/**
 * Calculates the weight recursively/on the fly instead of building the tree and calculating after
 */
public class RecursiveWeightCalculator implements RatingCalculator {

    private final Tangle tangle;
    private final SnapshotProvider snapshotProvider;

    /**
     * Constructor for Recursive Weight Calculator
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider accesses ledger's snapshots
     */
    public RecursiveWeightCalculator(Tangle tangle, SnapshotProvider snapshotProvider) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
    }

    @Override
    public UnIterableMap<HashId, Integer> calculate(Hash entryPoint) throws Exception {
        UnIterableMap<HashId, Integer> hashWeight = calculateRatingDfs(entryPoint);
        
        return hashWeight;
    }
    
    private UnIterableMap<HashId, Integer> calculateRatingDfs(Hash entryPoint) throws Exception {
        TransactionViewModel tvm = TransactionViewModel.fromHash(tangle, entryPoint);
        int depth = tvm.snapshotIndex() > 0 
                ? snapshotProvider.getLatestSnapshot().getIndex() - tvm.snapshotIndex() + 1 
                : 1;

        // Estimated capacity per depth, assumes 5 minute gap in between milestones, at 3tps
        UnIterableMap<HashId, Integer> hashWeight = createTxHashToCumulativeWeightMap( 5 * 60 * 3 * depth);

        Map<Hash, HashSet<Hash>> txToDirectApprovers = new HashMap<>();

        Deque<Hash> stack = new ArrayDeque<>();
        stack.push(entryPoint);

        while (CollectionUtils.isNotEmpty(stack)) {
            Hash txHash = stack.peekLast();

            HashSet<Hash> approvers = getTxDirectApproversHashes(txHash, txToDirectApprovers);
            if (null != approvers && (approvers.size() == 0 || hasAll(hashWeight, approvers, stack))) {
                approvers.add(txHash);
                hashWeight.put(txHash, getRating(approvers, txToDirectApprovers));
                stack.removeLast();
            } else {
                stack.addAll(approvers);
            }
        }

        return hashWeight;
    }

    private int getRating(HashSet<Hash> nonDupes, Map<Hash, HashSet<Hash>> txToDirectApprovers) throws Exception {
        Deque<Hash> stack = new ArrayDeque<>(nonDupes);
        while (CollectionUtils.isNotEmpty(stack)) {
            HashSet<Hash> approvers = getTxDirectApproversHashes(stack.pollLast(), txToDirectApprovers);
            for (Hash hash : approvers) {
                if (nonDupes.add(hash)) {
                    stack.add(hash);
                }
            }
        }

        return nonDupes.size();
    }

    private boolean hasAll(UnIterableMap<HashId, Integer> source, HashSet<Hash> requester, Deque<Hash> stack) {
        for (Hash h : requester) {
            if (!source.containsKey(h) && !stack.contains(h)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Finds the approvers of a transaction, and adds it to the txToDirectApprovers map if they werent there yet.
     * 
     * @param txHash The tx we find the approvers of
     * @param txToDirectApprovers The map we look in, and add to
     * @param fallback The map we check in before going in the database, can be <code>null</code>
     * @return
     * @throws Exception
     */
    private HashSet<Hash> getTxDirectApproversHashes(Hash txHash, Map<Hash, HashSet<Hash>> txToDirectApprovers)
            throws Exception {
        
        HashSet<Hash> txApprovers = txToDirectApprovers.get(txHash);
        if (txApprovers == null) {
            ApproveeViewModel approvers = ApproveeViewModel.load(tangle, txHash);
            Collection<Hash> appHashes = CollectionUtils.emptyIfNull(approvers.getHashes());
            txApprovers = new HashSet<>(appHashes.size());
            for (Hash appHash : appHashes) {
                // if not genesis (the tx that confirms itself)
                if (!snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(appHash)) {
                    txApprovers.add(appHash);
                }
            }
            txToDirectApprovers.put(txHash, txApprovers);
        }
        
        return new HashSet<Hash>(txApprovers);
    }
    
    private static UnIterableMap<HashId, Integer> createTxHashToCumulativeWeightMap(int size) {
        return new TransformingMap<>(size, HashPrefix::createPrefix, null);
    }
}
