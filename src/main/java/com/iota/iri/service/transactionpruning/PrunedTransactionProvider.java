package com.iota.iri.service.transactionpruning;

import java.util.Collection;

import com.iota.iri.model.Hash;

/**
 * Find, mark and store pruned transactions
 */
public interface PrunedTransactionProvider {
    
    /**
     * Checks if this transactions has been pruned
     * 
     * @param transactionHash The transaction to check for
     * @return <code>true</code> if it is, else <code>false</code>
     * @throws PrunedTransactionException If the provider fails to check the transaction
     */
    boolean containsTransaction(Hash transactionHash) throws PrunedTransactionException;

    /**
     * Mark a transaction as spent.
     * 
     * @param transactionHash the transaction which we want to mark.
     * @throws PrunedTransactionException If the provider fails to add the transaction
     */
    void addTransaction(Hash transactionHash) throws PrunedTransactionException;
    
    /**
     * Mark all transactions as pruned.
     * 
     * @param transactionHashes The transactions we want to mark
     * @throws PrunedTransactionException If the provider fails to add a transaction
     */
    void addTransactionBatch(Collection<Hash> transactionHashes) throws PrunedTransactionException;
}
