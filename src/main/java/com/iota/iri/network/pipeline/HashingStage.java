package com.iota.iri.network.pipeline;

import com.iota.iri.crypto.batched.BatchedHasher;

public class HashingStage {

    private BatchedHasher batchedHasher;

    public HashingStage(BatchedHasher batchedHasher) {
        this.batchedHasher = batchedHasher;
    }

    public ProcessingContext process(ProcessingContext ctx) {
        HashingPayload payload = (HashingPayload) ctx.getPayload();
        batchedHasher.submitHashingRequest(payload.getHashRequest());
        return ctx;
    }
}
