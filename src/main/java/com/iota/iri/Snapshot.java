package com.iota.iri;
import com.iota.iri.service.viewModels.TransactionViewModel;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public class Snapshot {

    public static final Map<BigInteger, Long> initialState = new HashMap<>();

    static {
        initialState.put(TransactionViewModel.PADDED_NULL_HASH, TransactionViewModel.SUPPLY);
    }
}
