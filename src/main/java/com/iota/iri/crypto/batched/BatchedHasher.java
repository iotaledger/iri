package com.iota.iri.crypto.batched;

/**
 * A BatchedHasher is a hasher which collects inputs in order
 * to perform optimized hashing by hashing multiple inputs at once.
 */
public interface BatchedHasher extends Runnable {

    /**
     * Default max timeout in milliseconds {@link BatchedHasher}s
     * await for a new incoming request before starting the batched hashing process.
     */
    int DEFAULT_BATCH_TIMEOUT_MILLISECONDS = 50;

    /**
     * Submits the given request to the {@link BatchedHasher} for processing.
     * The request's callback is executed within the thread of the BatchedHasher
     * up on completion of the processing.
     *
     * @param req The hashing request.
     */
    void submitHashingRequest(HashRequest req);
}
