package com.iota.iri.network;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;

public class TransactionRequester {

    private static final Logger log = LoggerFactory.getLogger(TransactionRequester.class);
    private final Set<Hash> transactionsToRequest = new LinkedHashSet<>();
    private final Set<Hash> recentlyRequestedTransactions = Collections.synchronizedSet(new HashSet<>());
    public static final int MAX_TX_REQ_QUEUE_SIZE = 10000;

    private final Object syncObj = new Object();
    private final Tangle tangle;
    private final SnapshotProvider snapshotProvider;

    /**
     * Create {@link TransactionRequester} for receiving transactions from the tangle.
     *
     * @param tangle           used to request transaction
     * @param snapshotProvider that allows to retrieve the {@link Snapshot} instances that are relevant for the node
     */
    public TransactionRequester(Tangle tangle, SnapshotProvider snapshotProvider) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
    }

    public Hash[] getRequestedTransactions() {
        synchronized (syncObj) {
            return transactionsToRequest.stream().toArray(Hash[]::new);
        }
    }

    public int numberOfTransactionsToRequest() {
        return transactionsToRequest.size();
    }

    public boolean clearTransactionRequest(Hash hash) {
        synchronized (syncObj) {
            return transactionsToRequest.remove(hash);
        }
    }

    /**
     * Adds the given transaction hash to the request queue.
     *
     * @param hash the hash of the transaction to add to the request queue
     */
    public void requestTransaction(Hash hash) {
        if (!snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(hash)) {
            synchronized (syncObj) {
                if (transactionsToRequestIsFull()) {
                    popEldestTransactionToRequest();
                }
                transactionsToRequest.add(hash);
            }
        }
    }

    /**
     * Puts the given transaction's trunk and branch transactions into the request queue if:
     * <ul>
     *     <li>the approver transaction is not solid</li>
     *     <li>trunk/branch is not a solid entry point</li>
     *     <li>trunk/branch is not persisted in the database</li>
     * </ul>
     *
     * @param approver the approver transaction
     */
    public void requestTrunkAndBranch(TransactionViewModel approver) throws Exception {
        // don't request anything if the approver is already solid
        if(approver.isSolid()){
            return;
        }
        Hash trunkHash = approver.getTrunkTransactionHash();
        Hash branchHash = approver.getBranchTransactionHash();
        if(!snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(trunkHash)
                && !TransactionViewModel.exists(tangle, trunkHash)){
            requestTransaction(trunkHash);
        }
        if(!snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(branchHash)
                && !TransactionViewModel.exists(tangle, branchHash)){
            requestTransaction(branchHash);
        }
    }

    /**
     * This method removes the oldest transaction in the transactionsToRequest Set.
     * <p>
     * It used when the queue capacity is reached, and new transactions would be dropped as a result.
     */
    @VisibleForTesting
    void popEldestTransactionToRequest() {
        Iterator<Hash> iterator = transactionsToRequest.iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    /**
     * This method allows to check if a transaction was requested by the TransactionRequester.
     * <p>
     * It can for example be used to determine if a transaction that was received by the node was actively requested
     * while i.e. solidifying transactions or if a transaction arrived due to the gossip protocol.
     *
     * @param transactionHash  hash of the transaction to check
     * @return true if the transaction is in the set of transactions to be requested and false otherwise
     */
    public boolean isTransactionRequested(Hash transactionHash) {
        return transactionsToRequest.contains(transactionHash);
    }

    /**
     * Checks whether the given transaction was recently requested on a neighbor.
     *
     * @param transactionHash hash of the transaction to check
     * @return true if the transaction was recently requested on a neighbor
     */
    public boolean wasTransactionRecentlyRequested(Hash transactionHash) {
        return recentlyRequestedTransactions.contains(transactionHash);
    }

    /**
     * Gets the amount of recently requested transactions.
     * @return the amount of recently requested transactions
     */
    public int numberOfRecentlyRequestedTransactions() {
        return recentlyRequestedTransactions.size();
    }

    /**
     * Clears the recently requested transactions set.
     */
    public void clearRecentlyRequestedTransactions(){
        recentlyRequestedTransactions.clear();
    }

    /**
     * Removes the given transaction hash from the recently requested transactions set.
     *
     * @param transactionHash hash of the transaction to remove
     * @return true if the transaction was recently requested and removed from the set
     */
    public boolean removeRecentlyRequestedTransaction(Hash transactionHash) {
        return recentlyRequestedTransactions.remove(transactionHash);
    }

    private boolean transactionsToRequestIsFull() {
        return transactionsToRequest.size() >= TransactionRequester.MAX_TX_REQ_QUEUE_SIZE;
    }


    public Hash transactionToRequest() throws Exception {
        // determine the first hash in our set that needs to be processed
        Hash hash = null;
        synchronized (syncObj) {
            // repeat while we have transactions that shall be requested
            if (transactionsToRequest.size() != 0) {
                // get the first item in our set for further examination
                Iterator<Hash> iterator = transactionsToRequest.iterator();
                hash = iterator.next();
                iterator.remove();
                recentlyRequestedTransactions.add(hash);
            }
        }

        // return our result
        return hash;
    }

}
