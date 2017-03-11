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

    public TimestampViewModel(byte[] bytes) {
        timestamp = new Timestamp();
        timestamp.bytes = bytes;
    }

    public Hash[] getTransactionHashes() {
        try {
            Timestamp timestampLoad = (Timestamp) Tangle.instance().load(Timestamp.class, timestamp.bytes).get();
            this.timestamp.transactions = timestampLoad.transactions;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return Arrays.stream(timestamp.transactions).map(transaction -> new Hash(transaction.hash)).toArray(Hash[]::new);
    }
}
