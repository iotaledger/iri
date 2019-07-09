package com.iota.iri.crypto.batched;

import com.iota.iri.crypto.Sponge;
import com.iota.iri.crypto.SpongeFactory;

/**
 * FakeBatchedCurl implements the {@link BatchedHasher} interface
 * but doesn't actually do any batching. The callbacks are called
 * within the thread which submits the hashing requests.
 */
public class FakeBatchedCurl implements BatchedHasher {

    private int hashLength;
    private Sponge spongeFunc;

    /**
     * Creates a new {@link FakeBatchedCurl} with the given
     * hash length and mode.
     *
     * @param hashLength the desired hash length
     * @param mode       the mode of the sponge function to use
     */
    public FakeBatchedCurl(int hashLength, SpongeFactory.Mode mode) {
        this.hashLength = hashLength;
        this.spongeFunc = SpongeFactory.create(mode);
    }

    @Override
    public void submitHashingRequest(HashRequest req) {
        spongeFunc.absorb(req.getInput(), 0, req.getInput().length);
        byte[] hashTrits = new byte[hashLength];
        spongeFunc.squeeze(hashTrits, 0, hashLength);
        req.getCallback().process(hashTrits);
        spongeFunc.reset();
    }

    @Override
    public void run() {
        // do nothing
    }
}
