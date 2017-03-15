package com.iota.iri.service.viewModels;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Timestamp;
import com.iota.iri.service.tangle.Tangle;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/**
 * Created by paul on 3/6/17 for iri.
 */
public class TimestampViewModel {
    private final Timestamp timestamp;

    public TimestampViewModel(Long val) {
        timestamp = new Timestamp();
        timestamp.value = val;
    }

    public Hash[] getTransactionHashes() throws ExecutionException, InterruptedException {
        Timestamp timestampLoad = (Timestamp) Tangle.instance().load(Timestamp.class, timestamp.value).get();
        this.timestamp.transactions = timestampLoad.transactions;
        return Arrays.stream(timestamp.transactions).map(transaction -> new Hash(transaction.hash)).toArray(Hash[]::new);
    }
}
