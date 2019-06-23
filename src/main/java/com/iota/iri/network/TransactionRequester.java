package com.iota.iri.network;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.zmq.MessageQ;
import com.iota.iri.storage.Tangle;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
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
    private final Map<Hash, Neighbor> milestoneTransactionsToRequest = new LinkedHashMap<>();
    private final Map<Hash, Neighbor> transactionsToRequest = new LinkedHashMap<>();

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

    public void init(double pRemoveRequest) {
        if(!initialized) {
            initialized = true;
            P_REMOVE_REQUEST = pRemoveRequest;
        }
    }

    public Hash[] getRequestedTransactions() {
        synchronized (syncObj) {
            return ArrayUtils.addAll(transactionsToRequest.keySet().toArray(new Hash[0]),
                    milestoneTransactionsToRequest.keySet().toArray(new Hash[0]));
        }
    }

    public int numberOfTransactionsToRequest() {
        return transactionsToRequest.size() + milestoneTransactionsToRequest.size();
    }

    public boolean clearTransactionRequest(Hash hash) {
        synchronized (syncObj) {
            boolean milestone = (milestoneTransactionsToRequest.remove(hash) != null);
            boolean normal = (transactionsToRequest.remove(hash) != null);
            return normal || milestone;
        }
    }

    public void requestTransaction(Hash hash, Neighbor neighbor, boolean milestone) throws Exception {
        if (!hash.equals(Hash.NULL_HASH) && !TransactionViewModel.exists(tangle, hash)) {
            synchronized (syncObj) {
                if(milestone) {
                    transactionsToRequest.remove(hash);
                    milestoneTransactionsToRequest.put(hash, neighbor);
                } else {
                    if(!milestoneTransactionsToRequest.containsKey(hash) && !transactionsToRequestIsFull()) {
                        transactionsToRequest.put(hash, neighbor);
                    }
                }
            }
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
        return (milestoneRequest && milestoneTransactionsToRequest.containsKey(transactionHash))
                || (!milestoneRequest && milestoneTransactionsToRequest.containsKey(transactionHash) ||
                transactionsToRequest.containsKey(transactionHash));
    }

    private boolean transactionsToRequestIsFull() {
        return transactionsToRequest.size() >= TransactionRequester.MAX_TX_REQ_QUEUE_SIZE;
    }

    public int getRequesterSleepPeriod() {
        if(transactionsToRequest.size() >= 1000) {
            return 10; // request every 10 millisecond
        } else if(transactionsToRequest.size() >= 100) {
            return 100;
        } else if(transactionsToRequest.size() >= 10) {
            return 500;
        } else if(transactionsToRequest.size() >= 2) {
            return 1000;
        } else {
            return 5000;
        }
    }

    public Pair<Hash, Neighbor> transactionToRequest(boolean milestone) throws Exception {
        // determine which set of transactions to operate on
        Map<Hash, Neighbor> primaryMap = milestone ? milestoneTransactionsToRequest : transactionsToRequest;
        Map<Hash, Neighbor> alternativeMap = milestone ? transactionsToRequest : milestoneTransactionsToRequest;
        Map<Hash, Neighbor> requestMap = primaryMap.size() == 0 ? alternativeMap : primaryMap;

        // determine the first pair in our set that needs to be processed
        Map.Entry<Hash, Neighbor> pair = null;
        synchronized (syncObj) {
            // repeat while we have transactions that shall be requested
            while (requestMap.size() != 0) {
                // remove the first item in our set for further examination
                Iterator<Map.Entry<Hash, Neighbor>> iterator = requestMap.entrySet().iterator();
                pair = iterator.next();
                iterator.remove();

                // if we have received the transaction in the mean time ....
                if (TransactionViewModel.exists(tangle, pair.getKey())) {
                    // ... dump a log message ...
                    log.info("Removed existing tx from request list: " + pair);
                    messageQ.publish("rtl %s", pair);

                    // ... and continue to the next element in the set
                    continue;
                }

                // ... otherwise -> re-add it at the end of the set ...
                //
                // Note: we always have enough space since we removed the element before
                requestMap.put(pair.getKey(), pair.getValue());

                // ... and abort our loop to continue processing with the element we found
                break;
            }
        }

        // randomly drop "non-milestone" transactions so we don't keep on asking for non-existent transactions forever
        if(random.nextDouble() < P_REMOVE_REQUEST && !requestMap.equals(milestoneTransactionsToRequest)) {
            synchronized (syncObj) {
                if (pair != null) {
                    transactionsToRequest.remove(pair.getKey());
                }
            }
        }

        // return our result
        if (pair == null) {
            return null;
        } else {
            return new ImmutablePair<>(pair.getKey(), pair.getValue());
        }
    }

}
