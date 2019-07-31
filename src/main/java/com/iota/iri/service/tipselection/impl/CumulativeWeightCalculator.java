package com.iota.iri.service.tipselection.impl;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.tipselection.RatingCalculator;
import com.iota.iri.storage.Tangle;

/**
 * Implementation of {@link RatingCalculator} that calculates the cumulative weight 
 * Calculates the weight recursively/on the fly for each transaction referencing {@code entryPoint}. <br>
 * Works using DFS search for new hashes and a BFS calculation. 
 * Uses cached values to prevent double database lookup for approvers
 */
public class CumulativeWeightCalculator implements RatingCalculator {

    private final Tangle tangle;
    private final SnapshotProvider snapshotProvider;

    /**
     * Constructor for Cumulative Weight Calculator
     * 
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider accesses ledger's snapshots
     */
    public CumulativeWeightCalculator(Tangle tangle, SnapshotProvider snapshotProvider) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
    }

    @Override
    public Map<Hash, Integer> calculate(Hash entryPoint) throws Exception {
        Map<Hash, Integer> hashWeightMap = calculateRatingDfs(entryPoint);
        
        return hashWeightMap;
    }
    
    private Map<Hash, Integer> calculateRatingDfs(Hash entryPoint) throws Exception {
        TransactionViewModel tvm = TransactionViewModel.fromHash(tangle, entryPoint);
        int depth = tvm.snapshotIndex() > 0 
                ? snapshotProvider.getLatestSnapshot().getIndex() - tvm.snapshotIndex() + 1 
                : 1;

        // Estimated capacity per depth, assumes 5 minute gap in between milestones, at 3tps
        Map<Hash, Integer> hashWeightMap = createTxHashToCumulativeWeightMap( 5 * 60 * 3 * depth);

        Map<Hash, Set<Hash>> txToDirectApprovers = new HashMap<>();

        Deque<Hash> stack = new ArrayDeque<>();
        stack.addAll(getTxDirectApproversHashes(entryPoint, txToDirectApprovers));

        while (!stack.isEmpty()) {
            Hash txHash = stack.pollLast();

            Set<Hash> approvers = getTxDirectApproversHashes(txHash, txToDirectApprovers);
            
            // If its empty, its a tip!
            if (approvers.isEmpty()) {
                hashWeightMap.put(txHash, 1);

            // Else we go deeper
            } else {
                // Add all approvers, given we didnt go there
                for (Hash h : approvers) {
                    if (!hashWeightMap.containsKey(h)) {
                        stack.add(h);
                    }
                }
                
                // Add the tx to the approvers list to count itself as +1 weight, preventing self-referencing
                approvers.add(txHash);
                
                // calculate and add rating. Naturally the first time all approvers need to be looked up. Then its cached.
                hashWeightMap.put(txHash, getRating(approvers, txToDirectApprovers));
            } 
        }

        // If we have a self-reference, its already added, otherwise we save a big calculation
        if (!hashWeightMap.containsKey(entryPoint)) {
            hashWeightMap.put(entryPoint, hashWeightMap.size() + 1);
        }
        return hashWeightMap;
    }

    /**
     * Gets the rating of a set, calculated by checking its approvers
     * 
     * @param startingSet All approvers of a certain hash, including the hash itself. 
     *                    Should always start with at least 1 hash.
     * @param txToDirectApproversCache The cache of approvers, used to prevent double db lookups
     * @return The weight, or rating, of the starting hash
     * @throws Exception If we can't get the approvers
     */
    private int getRating(Set<Hash> startingSet, Map<Hash, Set<Hash>> txToDirectApproversCache) throws Exception {
        Deque<Hash> stack = new ArrayDeque<>(startingSet);
        while (!stack.isEmpty()) {
            Set<Hash> approvers = getTxDirectApproversHashes(stack.pollLast(), txToDirectApproversCache);
            for (Hash hash : approvers) {
                if (startingSet.add(hash)) {
                    stack.add(hash);
                }
            }
        }

        return startingSet.size();
    }
    
    /**
     * Finds the approvers of a transaction, and adds it to the txToDirectApprovers map if they weren't there yet.
     * 
     * @param txHash The tx we find the approvers of
     * @param txToDirectApprovers The map we look in, and add to
     * @param fallback The map we check in before going in the database, can be <code>null</code>
     * @return A set with the direct approvers of the given hash
     * @throws Exception
     */
    private Set<Hash> getTxDirectApproversHashes(Hash txHash, Map<Hash, Set<Hash>> txToDirectApprovers)
            throws Exception {
        
        Set<Hash> txApprovers = txToDirectApprovers.get(txHash);
        if (txApprovers == null) {
            ApproveeViewModel approvers = ApproveeViewModel.load(tangle, txHash);
            Collection<Hash> appHashes;
            if (approvers == null || approvers.getHashes() == null) {
                appHashes = Collections.emptySet();
            } else {
                appHashes = approvers.getHashes();
            }
            
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
    
    private static Map<Hash, Integer> createTxHashToCumulativeWeightMap(int size) {
        return new HashMap<Hash, Integer>(size); //new TransformingMap<>(size, HashPrefix::createPrefix, null);
    }
}
