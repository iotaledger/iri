package com.iota.iri.network;

import com.iota.iri.controllers.TransactionRequester;

/**
 * Created by paul on 4/17/17.
 */
public class TipRequester extends TransactionRequester{
    private static final TransactionRequester instance = new TipRequester();

    public static TransactionRequester instance() {
        return instance;
    }
}
