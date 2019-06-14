package com.iota.iri.network.pipeline;

import com.iota.iri.crypto.batched.BatchedHasher;
import com.iota.iri.crypto.batched.HashRequest;

/**
 * The {@link HashingStage} batches up transaction trits and then hashes them using a {@link BatchedHasher} in one go.
 */
public class HashingStage implements Stage {

    private BatchedHasher batchedHasher;

    /**
     * Creates a new {@link HashingStage}.
     * 
     * @param batchedHasher The {@link BatchedHasher} to use
     */
    public HashingStage(BatchedHasher batchedHasher) {
        this.batchedHasher = batchedHasher;
    }

    /**
     * Extracts the {@link HashRequest} from the context and submits it to the {@link BatchedHasher}. The
     * {@link com.iota.iri.crypto.batched.HashRequest}'s callback must be setup to submit the result to the
     * {@link ValidationStage}.
     *
     * @param ctx the hashing stage {@link ProcessingContext}
     * @return the same ctx as passed in
     */
    @Override
    public ProcessingContext process(ProcessingContext ctx) {
        HashingPayload payload = (HashingPayload) ctx.getPayload();
        batchedHasher.submitHashingRequest(payload.getHashRequest());
        return ctx;
    }
}
