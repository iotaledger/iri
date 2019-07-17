package com.iota.iri.network.pipeline;

import com.iota.iri.crypto.batched.HashRequest;
import com.iota.iri.model.Hash;
import com.iota.iri.network.neighbor.Neighbor;

/**
 * Defines a payload which gets submitted to the {@link HashingStage}.
 */
public class HashingPayload extends ValidationPayload {

    private HashRequest hashRequest;

    /**
     * Creates a new {@link HashingPayload}.
     * 
     * @param neighbor          The neighbor from which the transaction originated from
     * @param txTrits           The transaction trits
     * @param txDigest          The transaction bytes digest
     * @param hashOfRequestedTx The hash of the requested transaction
     */
    public HashingPayload(Neighbor neighbor, byte[] txTrits, Long txDigest, Hash hashOfRequestedTx) {
        super(neighbor, txTrits, null, txDigest, hashOfRequestedTx);
    }

    /**
     * Gets the {@link HashRequest}.
     * 
     * @return the {@link HashRequest}
     */
    public HashRequest getHashRequest() {
        return hashRequest;
    }

    /**
     * Sets the {@link HashRequest}.
     *
     * @param hashRequest the {@link HashRequest} to set
     */
    public void setHashRequest(HashRequest hashRequest) {
        this.hashRequest = hashRequest;
    }
}
