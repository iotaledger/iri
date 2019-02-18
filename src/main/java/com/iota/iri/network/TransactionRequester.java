package com.iota.iri.network;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.*;

/**
 * Created by paul on 3/27/17.
 */
public class TransactionRequester {

    private static final Logger log = LoggerFactory.getLogger(TransactionRequester.class);
    private final Set<Hash> milestoneTransactionsToRequest = new LinkedHashSet<>();
    private final Set<Hash> transactionsToRequest = new LinkedHashSet<>();

    public static final int MAX_TX_REQ_QUEUE_SIZE = 10000;

    private static double P_REMOVE_REQUEST;
    private static boolean initialized = false;
    private final SecureRandom random = new SecureRandom();

    private final Object syncObj = new Object();
    private final Tangle tangle;
    private final SnapshotProvider snapshotProvider;

    /**
     * Create {@link TransactionRequester} for receiving transactions from the tangle.
     *
     * @param tangle used to request transaction
     * @param snapshotProvider that allows to retrieve the {@link Snapshot} instances that are relevant for the node
     */
    public TransactionRequester(Tangle tangle, SnapshotProvider snapshotProvider) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
    }

    public void init(double pRemoveRequest) {
        if(!initialized) {
            initialized = true;
            P_REMOVE_REQUEST = pRemoveRequest;
        }
    }

    public Hash[] getRequestedTransactions() {
        synchronized (syncObj) {
            return ArrayUtils.addAll(transactionsToRequest.stream().toArray(Hash[]::new),
                    milestoneTransactionsToRequest.stream().toArray(Hash[]::new));
        }
    }

    public int numberOfTransactionsToRequest() {
        return transactionsToRequest.size() + milestoneTransactionsToRequest.size();
    }

    public boolean clearTransactionRequest(Hash hash) {
        synchronized (syncObj) {
            boolean milestone = milestoneTransactionsToRequest.remove(hash);
            boolean normal = transactionsToRequest.remove(hash);
            return normal || milestone;
        }
    }

    public void requestTransaction(Hash hash, boolean milestone) throws Exception {
        if (!snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(hash) && !TransactionViewModel.exists(tangle, hash)) {
            synchronized (syncObj) {
                if(milestone) {
                    transactionsToRequest.remove(hash);
                    milestoneTransactionsToRequest.add(hash);
                } else {
                    if(!milestoneTransactionsToRequest.contains(hash)) {
                        if (transactionsToRequestIsFull()) {
                            popEldestTransactionToRequest();
                        }
                        transactionsToRequest.add(hash);
                    }
                }
            }
        }
    }

    /**
     * This method removes the oldest transaction in the transactionsToRequest Set.
     *
     * It used when the queue capacity is reached, and new transactions would be dropped as a result.
     */
    // @VisibleForTesting
    void popEldestTransactionToRequest() {
        Iterator<Hash> iterator = transactionsToRequest.iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    /**
     * This method allows to check if a transaction was requested by the TransactionRequester.
     *
     * It can for example be used to determine if a transaction that was received by the node was actively requested
     * while i.e. solidifying transactions or if a transaction arrived due to the gossip protocol.
     *
     * @param transactionHash hash of the transaction to check
     * @param milestoneRequest flag that indicates if the hash was requested by a milestone request
     * @return true if the transaction is in the set of transactions to be requested and false otherwise
     */
    public boolean isTransactionRequested(Hash transactionHash, boolean milestoneRequest) {
        return (milestoneRequest && milestoneTransactionsToRequest.contains(transactionHash))
                || (!milestoneRequest && milestoneTransactionsToRequest.contains(transactionHash) ||
                transactionsToRequest.contains(transactionHash));
    }

    private boolean transactionsToRequestIsFull() {
        return transactionsToRequest.size() >= TransactionRequester.MAX_TX_REQ_QUEUE_SIZE;
    }


    public Hash transactionToRequest(boolean milestone) throws Exception {
        // determine which set of transactions to operate on
        Set<Hash> primarySet = milestone ? milestoneTransactionsToRequest : transactionsToRequest;
        Set<Hash> alternativeSet = milestone ? transactionsToRequest : milestoneTransactionsToRequest;
        Set<Hash> requestSet = primarySet.size() == 0 ? alternativeSet : primarySet;

        // determine the first hash in our set that needs to be processed
        Hash hash = null;
        synchronized (syncObj) {
            // repeat while we have transactions that shall be requested
            while (requestSet.size() != 0) {
                // get the first item in our set for further examination
                Iterator<Hash> iterator = requestSet.iterator();
                hash = iterator.next();

                // if we have received the transaction in the mean time ....
                if (TransactionViewModel.exists(tangle, hash)) {
                    // we remove the transaction from the queue since we got it
                    iterator.remove();
                    // ... dump a log message ...
                    log.info("Removed existing tx from request list: " + hash);
                    tangle.publish("rtl %s", hash);

                    // ... and continue to the next element in the set
                    continue;
                }

                // ... and abort our loop to continue processing with the element we found
                break;
            }
        }

        // randomly drop "non-milestone" transactions so we don't keep on asking for non-existent transactions forever
        if(random.nextDouble() < P_REMOVE_REQUEST && !requestSet.equals(milestoneTransactionsToRequest)) {
            synchronized (syncObj) {
                transactionsToRequest.remove(hash);
            }
        }

        // return our result
        return hash;
    }

}
