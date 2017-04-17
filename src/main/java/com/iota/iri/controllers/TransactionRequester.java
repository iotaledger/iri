package com.iota.iri.controllers;

import com.iota.iri.conf.Configuration;
import com.iota.iri.model.Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static com.iota.iri.service.TipsManager.printNewSolidTransactions;

/**
 * Created by paul on 3/27/17.
 */
public class TransactionRequester {

    private static final TransactionRequester tipInstance = new TransactionRequester();
    private static final TransactionRequester milestoneInstance = new TransactionRequester();

    private final Logger log = LoggerFactory.getLogger(TransactionRequester.class);
    private static double P_REMOVE_REQUEST;
    private static boolean initialized = false;
    private final Set<Hash> transactionsToRequest = new HashSet<>();
    private final SecureRandom random = new SecureRandom();
    private volatile long lastTime = System.currentTimeMillis();
    public  static final int REQUEST_HASH_SIZE = 46;
    private static final byte[] NULL_REQUEST_HASH_BYTES = new byte[REQUEST_HASH_SIZE];

    public static void init(double p_REMOVE_REQUEST) {
        if(!initialized) {
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
        return transactionsToRequest.stream().toArray(Hash[]::new);
    }

    public static int getTotalNumberOfRequestedTransactions() {
        return tips().transactionsToRequest.size() + milestones().transactionsToRequest.size();
    }
    public int numberOfTransactionsToRequest() {
        return transactionsToRequest.size();
    }

    boolean clearTransactionRequest(Hash hash) {
        synchronized (this) {
            return transactionsToRequest.remove(hash);
        }
    }

    public void requestTransaction(Hash hash) throws ExecutionException, InterruptedException {
        if (!hash.equals(Hash.NULL_HASH) && !TransactionViewModel.exists(hash)) {
            synchronized (this) {
                transactionsToRequest.add(hash);
            }
        }
    }
    public void transactionToRequest(byte[] buffer, int offset) throws Exception {
        final long beginningTime = System.currentTimeMillis();
        Hash hash = null;
        if(transactionsToRequest.size() > 0) {
            while(hash == null) {
                hash = ((Hash) transactionsToRequest.toArray()[random.nextInt(transactionsToRequest.size())]);
                if(TransactionViewModel.exists(hash)) {
                    synchronized (this) {
                        log.info("Removing existing tx from request list: " + hash);
                        transactionsToRequest.remove(hash);
                    }
                    hash = null;
                }
                if(transactionsToRequest.size() == 0) {
                    break;
                }
            }
        }

        if(hash != null && !hash.equals(Hash.NULL_HASH)) {
            if(random.nextDouble() < P_REMOVE_REQUEST) {
                synchronized (this) {
                    transactionsToRequest.remove(hash);
                }
            }
            System.arraycopy(hash.bytes(), 0, buffer, offset, REQUEST_HASH_SIZE);
        } else {
            System.arraycopy(Hash.NULL_HASH.bytes(), 0, buffer, offset, REQUEST_HASH_SIZE);
        }
        long now = System.currentTimeMillis();
        if ((now - lastTime) > 10000L) {
            lastTime = now;
            log.info("Transactions to request = {}", transactionsToRequest.size() + " / " + TransactionViewModel.getNumberOfStoredTransactions() + " (" + (now - beginningTime) + " ms ). " );
        }
    }

    public boolean checkSolidity(Hash hash) throws Exception {
        Set<Hash> analyzedHashes = new HashSet<>(Collections.singleton(Hash.NULL_HASH));
        boolean solid = true;
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(hash));
        Hash hashPointer, trunkInteger, branchInteger;
        while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
            if (analyzedHashes.add(hashPointer)) {
                final TransactionViewModel transactionViewModel2 = TransactionViewModel.fromHash(hashPointer);
                if(!transactionViewModel2.isSolid()) {
                    if (transactionViewModel2.getType() == TransactionViewModel.PREFILLED_SLOT && !hashPointer.equals(Hash.NULL_HASH)) {
                        requestTransaction(hashPointer);
                        solid = false;
                        break;

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
            printNewSolidTransactions(analyzedHashes);
            TransactionViewModel.updateSolidTransactions(analyzedHashes);
        }
        return solid;
    }

    public static TransactionRequester tips() {
        return tipInstance;
    }
    public static TransactionRequester milestones() {
        return milestoneInstance;
    }
}
