package com.iota.iri;
import com.iota.iri.model.Hash;
import com.iota.iri.service.viewModels.TransactionViewModel;

import java.util.HashMap;
import java.util.Map;

public class Snapshot {

    public static final Map<Hash, Long> initialState = new HashMap<>();

    static {
        initialState.put(Hash.NULL_HASH, TransactionViewModel.SUPPLY);
    }
}
