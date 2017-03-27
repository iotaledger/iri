package com.iota.iri.service;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.service.viewModels.TransactionViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Created by paul on 3/27/17.
 */
public class TransactionRequester {
    private static final Logger log = LoggerFactory.getLogger(TransactionRequester.class);
    private static final int P_REMOVE_REQUEST = 10;
    private static Set<Hash> transactionsToRequest = new HashSet<>();
    private static SecureRandom random = new SecureRandom();
    private static volatile int requestIndex = 0;
    private static volatile long lastTime = System.currentTimeMillis();

    public static void rescanTransactionsToRequest() throws ExecutionException, InterruptedException {
        Hash[] missingTx = TransactionViewModel.getMissingTransactions();
        synchronized (TransactionViewModel.class) {
            transactionsToRequest.clear();
            transactionsToRequest.addAll(Arrays.asList(missingTx));
        }
    }
    public static Hash[] getRequestedTransactions() {
        return transactionsToRequest.stream().toArray(Hash[]::new);
    }

    public static int numberOfTransactionsToRequest() {
        return transactionsToRequest.size();
    }

    public static void clearTransactionRequest(Hash hash) {
        synchronized (TransactionViewModel.class) {
            transactionsToRequest.remove(hash);
        }
    }

    public static void requestTransaction(Hash hash) throws ExecutionException, InterruptedException {
        if (!hash.equals(Hash.NULL_HASH) && !TransactionViewModel.exists(hash)) {
            synchronized (TransactionViewModel.class) {
                transactionsToRequest.add(hash);
            }
        }
    }
    public static void transactionToRequest(byte[] buffer, int offset) throws Exception {
        final long beginningTime = System.currentTimeMillis();
        Hash hash = null;
        if(++requestIndex >= numberOfTransactionsToRequest()) {
            requestIndex = 0;
        }
        if(transactionsToRequest.size() > 0) {
            hash = ((Hash) transactionsToRequest.toArray()[requestIndex]);
        }

        if(hash != null && hash != null && !hash.equals(Hash.NULL_HASH)) {
            if(random.nextInt(P_REMOVE_REQUEST) == 0) {
                transactionsToRequest.remove(hash);
                new TransactionViewModel(new Transaction(hash)).store();
            }
            System.arraycopy(hash.bytes(), 0, buffer, offset, TransactionViewModel.HASH_SIZE);
        }
        long now = System.currentTimeMillis();
        if ((now - lastTime) > 10000L) {
            lastTime = now;
            log.info("Transactions to request = {}", transactionsToRequest.size() + " / " + TransactionViewModel.getNumberOfStoredTransactions() + " (" + (now - beginningTime) + " ms ). " );
        }
    }
}
