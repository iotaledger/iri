package com.iota.iri.network.pipeline;

import com.iota.iri.crypto.batched.HashRequest;
import com.iota.iri.model.Hash;
import com.iota.iri.network.neighbor.Neighbor;

public class HashingPayload extends ValidationPayload {
    private HashRequest hashRequest;

    public HashingPayload(Neighbor neighbor, byte[] txTrits, Long txDigest, Hash hashOfRequestedTx) {
        super(neighbor, txTrits, null, txDigest, hashOfRequestedTx);
    }

    public HashRequest getHashRequest() {
        return hashRequest;
    }

    public void setHashRequest(HashRequest hashRequest) {
        this.hashRequest = hashRequest;
    }
}
