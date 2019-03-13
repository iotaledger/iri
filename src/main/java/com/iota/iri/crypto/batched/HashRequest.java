package com.iota.iri.crypto.batched;

/**
 * A HashRequest represents a request against a {@link BatchedHasher} to hash
 * something and execute the given callback up on completion of the hashing.
 */
public class HashRequest {

    private byte[] input;
    private HashRequestCallback callback;

    /**
     * Creates a new {@link HashRequest} with the given input and callback.
     * @param input the trits input to hash
     * @param callback the callback to fire up on completion
     */
    public HashRequest(byte[] input, HashRequestCallback callback) {
        this.input = input;
        this.callback = callback;
    }

    /**
     * Gets the input of this {@link HashRequest}.
     * @return the input
     */
    public byte[] getInput() {
        return input;
    }

    /**
     * Gets the callback of this {@link HashRequest}.
     * @return the callback
     */
    public HashRequestCallback getCallback() {
        return callback;
    }
}
