package com.iota.iri.network.pipeline;

import com.iota.iri.model.Hash;
import com.iota.iri.network.neighbor.Neighbor;

import java.util.Optional;

public class ValidationPayload {
    private Neighbor neighbor;
    private byte[] txTrits;
    private byte[] hashTrits;
    private Long txBytesDigest;
    private Hash hashOfRequestedTx;

    public ValidationPayload(Neighbor neighbor, byte[] txTrits, byte[] hashTrits, Long txBytesDigest, Hash hashOfRequestedTx) {
        this.neighbor = neighbor;
        this.txBytesDigest = txBytesDigest;
        this.txTrits = txTrits;
        this.hashTrits = hashTrits;
        this.hashOfRequestedTx = hashOfRequestedTx;
    }

    public Optional<Neighbor> getNeighbor() {
        return neighbor == null ? Optional.empty() : Optional.of(neighbor);
    }

    public byte[] getTxTrits() {
        return txTrits;
    }

    public Optional<Long> getTxBytesDigest() {
        return txBytesDigest == null ? Optional.empty() : Optional.of(txBytesDigest);
    }

    public Optional<Hash> getHashOfRequestedTx() {
        return hashOfRequestedTx == null ? Optional.empty() : Optional.of(hashOfRequestedTx);
    }

    public byte[] getHashTrits() {
        return hashTrits;
    }

    public void setHashTrits(byte[] hashTrits) {
        this.hashTrits = hashTrits;
    }
}
