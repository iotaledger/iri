package com.iota.iri.network;

import com.iota.iri.controllers.TransactionRequester;

/**
 * Created by paul on 4/17/17.
 */
public class MilestoneTransactionRequester extends TransactionRequester {
    private static final TransactionRequester instance = new MilestoneTransactionRequester();

    public static TransactionRequester instance() {
        return instance;
    }
}
