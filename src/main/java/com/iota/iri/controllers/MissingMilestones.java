package com.iota.iri.controllers;

import com.iota.iri.model.Hash;

import java.util.concurrent.ExecutionException;

/**
 * Created by paul on 4/17/17.
 */
public class MissingMilestones extends TransactionRequester {
    private static final TransactionRequester instance = new MissingMilestones();

    @Override
    public void requestTransaction(Hash hash) throws ExecutionException, InterruptedException {
        MissingTipTransactions.instance().clearTransactionRequest(hash);
        super.requestTransaction(hash);
    }

    public static TransactionRequester instance() {
        return instance;
    }
}
