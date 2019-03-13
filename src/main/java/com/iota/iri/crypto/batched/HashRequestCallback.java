package com.iota.iri.crypto.batched;

/**
 * A callback executed with the result of a {@link HashRequest}.
 */
public interface HashRequestCallback {
    /**
     * The callback which handles the result hash trits.
     * @param trits the result hash trits
     */
    void process(byte[] trits);
}
