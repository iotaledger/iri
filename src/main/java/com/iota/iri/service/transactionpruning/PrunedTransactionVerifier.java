package com.iota.iri.service.transactionpruning;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;

/**
 * Verifies the integrity of a chain of pruned transactions by checking each trunk/branch.
 */
public interface PrunedTransactionVerifier {

    /**
     * Does a preliminary check to see if we should continue checking this hash its pruned status
     * 
     * @param hash
     * @return
     * @throws PrunedTransactionException
     */
    boolean isPossiblyPruned(Hash hash) throws PrunedTransactionException;

    /**
     * Performs multiple checks on the hash to ensure it is pruned before
     * 
     * @param hash
     * @return
     * @throws PrunedTransactionException
     */
    boolean isPruned(Hash hash) throws PrunedTransactionException;

    /**
     * Sends the transaction data for the verifier to use/store as it needs.
     * {@link #waitingForHash(Hash)} should be used before to determine if we actually need it
     * 
     * @param receivedTransactionViewModel
     * @throws PrunedTransactionException
     */
    void submitTransaction(TransactionViewModel receivedTransactionViewModel) throws PrunedTransactionException;

    /**
     * Checks if we are waiting for this transaction its information.
     * THis would mean that {@link #isPruned(Hash)} is called, and one of its parents has a reference to this hash.
     * 
     * @param hash
     * @return
     * @throws PrunedTransactionException
     */
    boolean waitingForHash(Hash hash) throws PrunedTransactionException;

}
