package com.iota.iri;

import java.util.HashMap;
import java.util.Map;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;

public class Snapshot {

    public static final Map<Hash, Long> initialState = new HashMap<>();

    static {
        initialState.put(Hash.NULL_HASH, Transaction.SUPPLY);
    }
}
