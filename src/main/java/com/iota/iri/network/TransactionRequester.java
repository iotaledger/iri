package com.iota.iri.network;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.zmq.MessageQ;
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
    private final MessageQ messageQ;
    private final Set<Hash> milestoneTransactionsToRequest = new LinkedHashSet<>();
    private final Set<Hash> transactionsToRequest = new LinkedHashSet<>();

    public static final int MAX_TX_REQ_QUEUE_SIZE = 10000;

    private static double P_REMOVE_REQUEST;
    private static boolean initialized = false;
    private final SecureRandom random = new SecureRandom();

    private final Object syncObj = new Object();
    private final Tangle tangle;

    public TransactionRequester(Tangle tangle, MessageQ messageQ) {
        this.tangle = tangle;
        this.messageQ = messageQ;
    }

    public void init(double p_REMOVE_REQUEST) {
        if(!initialized) {
            initialized = true;
            P_REMOVE_REQUEST = p_REMOVE_REQUEST;
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
        if (!hash.equals(Hash.NULL_HASH) && !TransactionViewModel.exists(tangle, hash)) {
            synchronized (syncObj) {
                if(milestone) {
                    transactionsToRequest.remove(hash);
                    milestoneTransactionsToRequest.add(hash);
                } else {
                    if(!milestoneTransactionsToRequest.contains(hash) && !transactionsToRequestIsFull()) {
                        transactionsToRequest.add(hash);
                    }
                }
            }
        }
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
                // remove the first item in our set for further examination
                Iterator<Hash> iterator = requestSet.iterator();
                hash = iterator.next();
                iterator.remove();

                // if we have received the transaction in the mean time ....
                if (TransactionViewModel.exists(tangle, hash)) {
                    // ... dump a log message ...
                    log.info("Removed existing tx from request list: " + hash);
                    messageQ.publish("rtl %s", hash);

                    // ... and continue to the next element in the set
                    continue;
                }

                // ... otherwise -> re-add it at the end of the set ...
                //
                // Note: we always have enough space since we removed the element before
                requestSet.add(hash);

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
