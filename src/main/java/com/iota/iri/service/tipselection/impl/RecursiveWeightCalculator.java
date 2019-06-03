package com.iota.iri.service.tipselection.impl;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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
    
    private Map<Hash, ArrayDeque<Hash>> txToDirectApprovers = new HashMap<>();

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
        
        Deque<Hash> stack = new ArrayDeque<>();
        Map<Hash, ArrayDeque<Hash>> txToDirectApprovers = new HashMap<>();

        stack.push(entryPoint);
        while (CollectionUtils.isNotEmpty(stack)) {
            Hash txHash = stack.peek();
            if (!hashWeight.containsKey(txHash)) {
                Collection<Hash> appHashes = getTxDirectApproversHashes(txHash, txToDirectApprovers, txToDirectApprovers);
                if (CollectionUtils.isNotEmpty(appHashes)) {
                    Hash txApp = getAndRemoveApprover(appHashes);
                    stack.push(txApp);
                    continue;
                }
            } else {
                stack.pop();
                continue;
            }
            
            HashSet<HashId> set = new HashSet<>();
            set.add(txHash);
            
            int rating = txHash.equals(entryPoint) ? hashWeight.size() + 1 : getRating(txHash, set);
            hashWeight.put(txHash, rating);
        }
        return hashWeight;
    }

    private int getRating(Hash hash, Set<HashId> seenHashes) throws Exception {
        int weight = 1;

        ArrayDeque<Hash> approvers = getTxDirectApproversHashes(hash, txToDirectApprovers, null);
        for (Hash approver : approvers) {
            if (!seenHashes.contains(approver)) {
                seenHashes.add(approver);
                weight += getRating(approver, seenHashes);
            }
        }
        
        return weight;
    }
    
    private Hash getAndRemoveApprover(Collection<Hash> appHashes) {
        Iterator<Hash> hashIterator = appHashes.iterator();
        Hash txApp = hashIterator.next();
        hashIterator.remove();
        return txApp;
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
    private ArrayDeque<Hash> getTxDirectApproversHashes(Hash txHash,  
            Map<Hash, ArrayDeque<Hash>> txToDirectApprovers, 
            Map<Hash, ArrayDeque<Hash>> fallback) 
            throws Exception {
        
        ArrayDeque<Hash> txApprovers = txToDirectApprovers.get(txHash);
        if (txApprovers == null) {
            if (fallback != null && fallback.containsKey(txHash)) {
                txApprovers = fallback.get(txHash);
                txToDirectApprovers.put(txHash, txApprovers);
            } else {
                ApproveeViewModel approvers = ApproveeViewModel.load(tangle, txHash);
                Collection<Hash> appHashes = CollectionUtils.emptyIfNull(approvers.getHashes());
                txApprovers = new ArrayDeque<>(appHashes.size());
                for (Hash appHash : appHashes) {
                    //if not genesis (the tx that confirms itself)
                    if (!snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(appHash)) {
                        txApprovers.add(appHash);
                    }
                }
                txToDirectApprovers.put(txHash, txApprovers);
            }
        }
        return txApprovers;
    }
    
    private static UnIterableMap<HashId, Integer> createTxHashToCumulativeWeightMap(int size) {
        return new TransformingMap<>(size, HashPrefix::createPrefix, null);
    }
}
