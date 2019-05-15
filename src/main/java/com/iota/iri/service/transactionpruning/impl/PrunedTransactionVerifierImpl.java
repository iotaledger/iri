package com.iota.iri.service.transactionpruning.impl;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.transactionpruning.PrunedTransactionException;
import com.iota.iri.service.transactionpruning.PrunedTransactionProvider;
import com.iota.iri.service.transactionpruning.PrunedTransactionVerifier;

public class PrunedTransactionVerifierImpl implements PrunedTransactionVerifier {
    
    private static final int PRUNED_CERTAIN = 10;
    
    private PrunedTransactionProvider provider;

    private TransactionRequester requester;
    
    /**
     * List of Hashes who were possibly pruned, but turned out false after verifying
     */
    private List<Hash> verifiedFalse;
    
    /**
     * List of children we have tested per main tx hash
     */
    private Map<Hash, List<Hash>> parents;
    
    /**
     * Map of requested tx and our certainty it beeing pruned
     */
    private Map<Hash, Integer> prunedHashTest;



    public PrunedTransactionVerifierImpl(PrunedTransactionProvider provider, TransactionRequester requester) {
        this.provider = provider;
        this.requester = requester;
        
        verifiedFalse = new LinkedList<>();
        prunedHashTest = new HashMap<>();
    }
    
    /**
     * Should be called before adding the transaction hash to ensure the initial hash is pruned
     *  
     * @return
     * @throws PrunedTransactionException 
     */
    @Override
    public boolean isPossiblyPruned(Hash hash) throws PrunedTransactionException {
        if (verifiedFalse.contains(hash)) {
            return false;
        }
        return provider.containsTransaction(hash);
    }

    /**
     * 
     * {@inheritDoc}
     */
    @Override
    public boolean isPruned(Hash hash) throws PrunedTransactionException{
        if (verifiedFalse.contains(hash)) {
            return false;
        }
        
        if (!prunedHashTest.containsKey(hash)) {
            try {
                initializeVerify(hash);
            } catch (Exception e) {
                throw new PrunedTransactionException("Failed to initialize pruned lookup", e);
            }
        }
        
        return prunedHashTest.get(hash) >= PRUNED_CERTAIN; 
    }

    /**
     * 
     * {@inheritDoc}
     */
    @Override
    public void submitTransaction(TransactionViewModel receivedTransactionViewModel) throws PrunedTransactionException {
        Hash parent = receivedTransactionViewModel.getHash();
        Hash child = getChildForParent(parent);
        if (child == null || isPruned(child)) {
            // We succeeded in the meantime.
            return;
        }
        
        if (isPossiblyPruned(parent)) {
            // Add one to the map
            prunedHashTest.merge(child, 1, Integer::sum);
            
            if (isPruned(child)) {
                // We succeeded in the meantime.
                parents.remove(child);
                return;
            }
            
            List<Hash> parents = getParentsFor(child);
            
            // It could that they already got referenced through another tx
            try {
                if (!parents.contains(receivedTransactionViewModel.getBranchTransactionHash())){
                    parents.add(receivedTransactionViewModel.getBranchTransactionHash());
                    request(receivedTransactionViewModel.getBranchTransactionHash());
                }
                if (!parents.contains(receivedTransactionViewModel.getTrunkTransactionHash())){
                    parents.add(receivedTransactionViewModel.getTrunkTransactionHash());
                    request(receivedTransactionViewModel.getTrunkTransactionHash());
                }
            } catch (Exception e) {
                // We need to request but failed to do so
                throw new PrunedTransactionException(e);
            }
        } else {
            // False positive
            clean(child);
            verifiedFalse.add(child);
        }
    }
    
    /**
     * 
     * {@inheritDoc}
     */
    @Override
    public boolean waitingForHash(Hash hash) {
        return getChildForParent(hash) != null;
    }
    
    private void clean(Hash child) {
        prunedHashTest.remove(child);
        parents.remove(child);
    }

    private void request(Hash hash) throws Exception {
        requester.requestTransaction(hash, false);
    }

    private List<Hash> addParentForChild(Hash parent, Hash child) {
        List<Hash> list = getParentsFor(child);
        
        list.add(parent);
        return list;
    }

    private List<Hash> getParentsFor(Hash child) {
        if (parents == null) {
            parents = new HashMap<>();
        }
        
        List<Hash> list;
        if (parents.containsKey(child)) {
            list = parents.get(child);
        } else {
            list = new LinkedList<>();
            parents.put(child, list);
        }
        return list;
    }
    
    private void initializeVerify(Hash hash) throws Exception {
        addParentForChild(hash, hash);
        prunedHashTest.put(hash, 1);
        request(hash);
    }
    
    private Hash getChildForParent(Hash parent) {
        if (parents == null) {
            return null;
        }
        
        for (Entry<Hash, List<Hash>> entry : parents.entrySet()) {
            if (entry.getValue().contains(parent)) {
                return entry.getKey();
            }
        }
        
        return null;
    }
}
