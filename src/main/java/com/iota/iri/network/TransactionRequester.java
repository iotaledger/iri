package com.iota.iri.network;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;
import com.iota.iri.zmq.MessageQ;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by paul on 3/27/17.
 */
public class TransactionRequester {

    private static final Logger log = LoggerFactory.getLogger(TransactionRequester.class);
    private final MessageQ messageQ;
    private final Set<Hash> milestoneTransactionsToRequest = new LinkedHashSet<>();
    private final Set<Hash> transactionsToRequest = new LinkedHashSet<>();

    public static final int MAX_TX_REQ_QUEUE_SIZE = 10000;

    public static final int REQUEST_HASH_SIZE = 46;


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
        if (!initialized) {
            initialized = true;
            P_REMOVE_REQUEST = p_REMOVE_REQUEST;
            log.info("P_REMOVE_REQUEST = {}", P_REMOVE_REQUEST);
        }
    }

    private boolean isP_REMOVE_REQUEST() {
        return random.nextDouble() < P_REMOVE_REQUEST;
    }


    public Hash[] getRequestedTransactions() {
        synchronized (syncObj) {
            return ArrayUtils.addAll(transactionsToRequest.stream().toArray(Hash[]::new),
                    milestoneTransactionsToRequest.stream().toArray(Hash[]::new));
        }
    }

    public int numberOfTransactionsToRequest() {
        synchronized (syncObj) {
            return transactionsToRequest.size() + milestoneTransactionsToRequest.size();
        }
    }

    public boolean clearTransactionRequest(Hash hash) {
        synchronized (syncObj) {
            boolean milestone = milestoneTransactionsToRequest.remove(hash);
            boolean normal = transactionsToRequest.remove(hash);
            return normal || milestone;
        }
    }

    public void requestTransaction(Hash hash, boolean milestone) throws Exception {
        if (hash.equals(Hash.NULL_HASH) || TransactionViewModel.exists(tangle, hash)) {
            return;
        }
        synchronized (syncObj) {
            if (milestone) {
                transactionsToRequest.remove(hash);
                milestoneTransactionsToRequest.add(hash);
            } else {
                if (!milestoneTransactionsToRequest.contains(hash) && !transactionsToRequestIsFull()) {
                    transactionsToRequest.add(hash);
                }
            }
        }
    }

    private boolean transactionsToRequestIsFull() {
        synchronized (syncObj) {
            return transactionsToRequest.size() >= TransactionRequester.MAX_TX_REQ_QUEUE_SIZE;
        }
    }


    public Hash transactionToRequest(boolean milestone) throws Exception {
        Hash hash = null;
        Set<Hash> requestSet;
        synchronized (syncObj) {
            if (milestone) {
                requestSet = milestoneTransactionsToRequest;
                if (requestSet.size() == 0) {
                    requestSet = transactionsToRequest;
                }
            } else {
                requestSet = transactionsToRequest;
                if (requestSet.size() == 0) {
                    requestSet = milestoneTransactionsToRequest;
                }
            }
            while (requestSet.size() != 0) {
                Iterator<Hash> iterator = requestSet.iterator();
                hash = iterator.next();
                iterator.remove();
                if (TransactionViewModel.exists(tangle, hash)) {
                    log.info("Removed existing tx from request list: " + hash);
                    messageQ.publish("rtl %s", hash);
                } else {
                    if (!transactionsToRequestIsFull()) {
                        requestSet.add(hash);
                    }
                    break;
                }
            }
            if (isP_REMOVE_REQUEST() && !requestSet.equals(milestoneTransactionsToRequest)) {
                transactionsToRequest.remove(hash);
            }
        }
        return hash;
    }
}
