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
    private static volatile long lastTime = System.currentTimeMillis();
    public  static final int REQUEST_HASH_SIZE = 49;
    private static final byte[] NULL_REQUEST_HASH_BYTES = new byte[REQUEST_HASH_SIZE];

    private static double P_REMOVE_REQUEST;
    private static int RESCAN_SLEEP_NANOS = 20000;
    private static boolean initialized = false;
    private final SecureRandom random = new SecureRandom();

    private final Object syncObj = new Object();
    private static Thread rescanThread;
    private final Tangle tangle;

    public TransactionRequester(Tangle tangle, MessageQ messageQ) {
        this.tangle = tangle;
        this.messageQ = messageQ;
    }

    public void init(double p_REMOVE_REQUEST) {
        if(!initialized) {
            initialized = true;
            P_REMOVE_REQUEST = p_REMOVE_REQUEST;

            /*
            rescanThread = new Thread(() -> {
                try {
                    rescanTransactionsToRequest();
                } catch (Exception e) {
                    log.error("Could rescan transactions", e);
                }
            }, "Rescan Transactions Thread");
            rescanThread.start();
            */
        }
    }

    public void shutdown() {
        if(rescanThread != null) {
            try {
                rescanThread.join();
            } catch (InterruptedException e) {
            }
        }
    }


    /*
    private void rescanTransactionsToRequest() throws Exception {

        Set<Indexable> missingTransactions = TransactionViewModel.getMissingTransactions();
        if(missingTransactions != null) {
            for(Indexable hash : missingTransactions) {
                requestTransaction((Hash) hash, false);

            }
        }
        TransactionViewModel transaction = TransactionViewModel.first();
        if(transaction != null) {
            transaction.quickSetSolid();
            while (!(transaction = transaction.next()).getHash().equals(Hash.NULL_HASH)) {
                transaction.quickSetSolid();
                Thread.sleep(0, RESCAN_SLEEP_NANOS);
            }
        }
    }
        */

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

    public void requestTransactions(Set<Hash> hashes, boolean milestone) throws Exception {
        synchronized (syncObj) {
            for(Hash hash: hashes) {
                requestTransaction(hash, milestone);
            }
        }
    }

    public void requestTransaction(Hash hash, boolean milestone) throws Exception {
        if (!hash.equals(Hash.NULL_HASH) && !TransactionViewModel.exists(tangle, hash)) {
            synchronized (syncObj) {
                if(milestone) {
                    transactionsToRequest.remove(hash);
                    milestoneTransactionsToRequest.add(hash);
                } else {
                    if(!milestoneTransactionsToRequest.contains(hash)) {
                        transactionsToRequest.add(hash);
                    }
                }
            }
        }
    }


    public Hash transactionToRequest(boolean milestone) throws Exception {
        final long beginningTime = System.currentTimeMillis();
        Hash hash = null;
        Set<Hash> requestSet;
        if(milestone) {
             requestSet = milestoneTransactionsToRequest;
             if(requestSet.size() == 0) {
                 requestSet = transactionsToRequest;
             }
        } else {
            requestSet = transactionsToRequest;
            if(requestSet.size() == 0) {
                requestSet = milestoneTransactionsToRequest;
            }
        }
        synchronized (syncObj) {
            while (requestSet.size() != 0) {
                Iterator<Hash> iterator = requestSet.iterator();
                hash = iterator.next();
                iterator.remove();
                if (TransactionViewModel.exists(tangle, hash)) {
                    log.info("Removed existing tx from request list: " + hash);
                    messageQ.publish("rtl %s", hash);
                } else {
                    requestSet.add(hash);
                    break;
                }
            }
        }

        if(random.nextDouble() < P_REMOVE_REQUEST && !requestSet.equals(milestoneTransactionsToRequest)) {
            synchronized (syncObj) {
                transactionsToRequest.remove(hash);
            }
        }

        long now = System.currentTimeMillis();
        if ((now - lastTime) > 10000L) {
            lastTime = now;
            //log.info("Transactions to request = {}", numberOfTransactionsToRequest() + " / " + TransactionViewModel.getNumberOfStoredTransactions() + " (" + (now - beginningTime) + " ms ). " );
        }
        return hash;
    }

}
