package com.iota.iri.network.pipeline;

import com.iota.iri.model.Hash;
import com.iota.iri.network.neighbor.Neighbor;

public class ReplyPayload {
    private Neighbor neighbor;
    private Hash hashOfRequestedTx;

    public ReplyPayload(Neighbor neighbor, Hash hashOfRequestedTx) {
        this.neighbor = neighbor;
        this.hashOfRequestedTx = hashOfRequestedTx;
    }

    public Neighbor getNeighbor() {
        return neighbor;
    }

    public void setNeighbor(Neighbor neighbor) {
        this.neighbor = neighbor;
    }

    public Hash getHashOfRequestedTx() {
        return hashOfRequestedTx;
    }
}
