package com.iota.iri.controllers;

import com.iota.iri.model.Hash;

import java.security.SecureRandom;
import java.util.concurrent.ExecutionException;

/**
 * Created by paul on 4/17/17.
 */
public class MissingTipTransactions extends TransactionRequester{
    private static double P_REMOVE_REQUEST;
    private static boolean initialized = false;
    private static final TransactionRequester instance = new MissingTipTransactions();
    private final SecureRandom random = new SecureRandom();

    public static void init(double p_REMOVE_REQUEST) {
        if(!initialized) {
            initialized = true;
            P_REMOVE_REQUEST = p_REMOVE_REQUEST;
        }
    }

    @Override
    public void requestTransaction(Hash hash) throws ExecutionException, InterruptedException {
        super.requestTransaction(hash);
    }

    @Override
    public Hash transactionToRequest() throws Exception {
        Hash hash = super.transactionToRequest();
        /*
        if(hash != null && !hash.equals(Hash.NULL_HASH)) {
            System.arraycopy(hash.bytes(), 0, buffer, offset, REQUEST_HASH_SIZE);
        } else {
            System.arraycopy(Hash.NULL_HASH.bytes(), 0, buffer, offset, REQUEST_HASH_SIZE);
        }
        */
        if(random.nextDouble() < P_REMOVE_REQUEST) {
            synchronized (this) {
                clearTransactionRequest(hash);
            }
        }
        return hash;
    }

    public static TransactionRequester instance() {
        return instance;
    }
}
