package com.iota.iri.controllers;

import com.iota.iri.conf.Configuration;
import com.iota.iri.model.Hash;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Created by paul on 3/27/17.
 */
public class TransactionRequester {

    private final Logger log = LoggerFactory.getLogger(TransactionRequester.class);
    private final Set<Hash> milestoneTransactionsToRequest = new LinkedHashSet<>();
    private final Set<Hash> transactionsToRequest = new LinkedHashSet<>();
    private static volatile long lastTime = System.currentTimeMillis();
    public  static final int REQUEST_HASH_SIZE = 49;
    private static final byte[] NULL_REQUEST_HASH_BYTES = new byte[REQUEST_HASH_SIZE];

    private static double P_REMOVE_REQUEST;
    private static boolean initialized = false;
    private static final TransactionRequester instance = new TransactionRequester();
    private final SecureRandom random = new SecureRandom();

    public static void init(double p_REMOVE_REQUEST) {
        if(!initialized) {
            initialized = true;
            P_REMOVE_REQUEST = p_REMOVE_REQUEST;
        }
    }

    public void rescanTransactionsToRequest() throws ExecutionException, InterruptedException {
        Hash[] missingTx = TransactionViewModel.getMissingTransactions();
        synchronized (this) {
            transactionsToRequest.clear();
            transactionsToRequest.addAll(Arrays.asList(missingTx));
        }
    }
    public Hash[] getRequestedTransactions() {
        return ArrayUtils.addAll(transactionsToRequest.stream().toArray(Hash[]::new),
                milestoneTransactionsToRequest.stream().toArray(Hash[]::new));
    }

    public int numberOfTransactionsToRequest() {
        return transactionsToRequest.size() + milestoneTransactionsToRequest.size();
    }

    boolean clearTransactionRequest(Hash hash) {
        synchronized (this) {
            boolean milestone = milestoneTransactionsToRequest.remove(hash);
            boolean normal = transactionsToRequest.remove(hash);
            return normal || milestone;
        }
    }

    public void requestTransaction(Hash hash, boolean milestone) throws ExecutionException, InterruptedException {
        if (!hash.equals(Hash.NULL_HASH) && !TransactionViewModel.exists(hash)) {
            synchronized (this) {
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
        synchronized (this) {
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
            while(requestSet.size() != 0) {
                //hash = (Hash) requestSet.toArray()[random.nextInt(requestSet.size())];
                Iterator<Hash> iterator = requestSet.iterator();
                hash = iterator.next();
                iterator.remove();
                if(TransactionViewModel.exists(hash)) {
                    log.info("Removed existing tx from request list: " + hash);
                } else {
                    requestSet.add(hash);
                    break;
                }
            }
        }

        if(random.nextDouble() < P_REMOVE_REQUEST && !requestSet.equals(milestoneTransactionsToRequest)) {
            synchronized (this) {
                transactionsToRequest.remove(hash);
            }
        }

        long now = System.currentTimeMillis();
        if ((now - lastTime) > 10000L) {
            lastTime = now;
            log.info("Transactions to request = {}", numberOfTransactionsToRequest() + " / " + TransactionViewModel.getNumberOfStoredTransactions() + " (" + (now - beginningTime) + " ms ). " );
        }
        return hash;
    }

    public boolean checkSolidity(Hash hash, boolean milestone) throws Exception {
        Set<Hash> analyzedHashes = new HashSet<>(Collections.singleton(Hash.NULL_HASH));
        boolean solid = true;
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(hash));
        Hash hashPointer, trunkInteger, branchInteger;
        while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
            if (analyzedHashes.add(hashPointer)) {
                final TransactionViewModel transactionViewModel2 = TransactionViewModel.fromHash(hashPointer);
                if(!transactionViewModel2.isSolid()) {
                    if (transactionViewModel2.getType() == TransactionViewModel.PREFILLED_SLOT && !hashPointer.equals(Hash.NULL_HASH)) {
                        requestTransaction(hashPointer, milestone);
                        solid = false;
                    } else {
                        trunkInteger = transactionViewModel2.getTrunkTransactionHash();
                        branchInteger = transactionViewModel2.getBranchTransactionHash();
                        nonAnalyzedTransactions.offer(trunkInteger);
                        nonAnalyzedTransactions.offer(branchInteger);
                    }
                }
            }
        }
        if (solid) {
            TransactionViewModel.updateSolidTransactions(analyzedHashes);
        }
        return solid;
    }

    public static TransactionRequester instance() {
        return instance;
    }
}
