package com.iota.iri.service.validation;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;

import java.util.Set;

public interface TransactionSolidifier {

    /**
     * Initialize the executor service. Start processing transactions to solidify.
     */
    void start();

    /**
     * Interrupt thread processes and shut down the executor service.
     */
    void shutdown();

    /**
     * Add a hash to the solidification queue, and runs an initial {@link #checkSolidity} call.
     *
     * @param hash      Hash of the transaction to solidify
     */
    void addToSolidificationQueue(Hash hash);

    /**
     * Fetch a copy of the current transactionsToBroadcast set.
     * @return          A set of {@link TransactionViewModel} objects to be broadcast.
     */
    Set<TransactionViewModel> getBroadcastQueue();

    /**
     * Remove any broadcasted transactions from the transactionsToBroadcast set
     * @param transactionsBroadcasted   A set of {@link TransactionViewModel} objects to remove from the set.
     */
    void clearFromBroadcastQueue(Set<TransactionViewModel> transactionsBroadcasted);

    /**
     * This method does the same as {@link #checkSolidity(Hash, int)} but defaults to an unlimited amount
     * of transactions that are allowed to be traversed.
     *
     * @param hash hash of the transactions that shall get checked
     * @return true if the transaction is solid and false otherwise
     * @throws Exception if anything goes wrong while trying to solidify the transaction
     */
    boolean checkSolidity(Hash hash) throws Exception;

    /**
     * This method checks transactions for solidity and marks them accordingly if they are found to be solid.
     *
     * It iterates through all approved transactions until it finds one that is missing in the database or until it
     * reached solid transactions on all traversed subtangles. In case of a missing transactions it issues a transaction
     * request and returns false. If no missing transaction is found, it marks the processed transactions as solid in
     * the database and returns true.
     *
     * Since this operation can potentially take a long time to terminate if it would have to traverse big parts of the
     * tangle, it is possible to limit the amount of transactions that are allowed to be processed, while looking for
     * unsolid / missing approvees. This can be useful when trying to "interrupt" the solidification of one transaction
     * (if it takes too many steps) to give another one the chance to be solidified instead (i.e. prevent blocks in the
     * solidification threads).
     *
     * @param hash hash of the transactions that shall get checked
     * @param maxProcessedTransactions the maximum amount of transactions that are allowed to be traversed
     * @return true if the transaction is solid and false otherwise
     * @throws Exception if anything goes wrong while trying to solidify the transaction
     */
    boolean checkSolidity(Hash hash, int maxProcessedTransactions) throws Exception;

}
