package com.iota.iri.viewModel;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Timestamp;
import com.iota.iri.tangle.Tangle;

import java.util.Arrays;

/**
 * Created by paul on 3/6/17 for iri.
 */
public class TimestampViewModel {
    private final Timestamp timestamp;

    public TimestampViewModel(byte[] bytes) {
        timestamp = new Timestamp();
        timestamp.bytes = bytes;
    }

    public Hash[] getTransactionHashes() {
        Tangle.instance().load(timestamp, timestamp.bytes);
        return Arrays.stream(timestamp.transactions).map(transaction -> new Hash(transaction.hash)).toArray(Hash[]::new);
    }
}
