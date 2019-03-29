package com.iota.iri.network.pipeline;

import com.iota.iri.network.neighbor.Neighbor;

import java.nio.ByteBuffer;

public class PreProcessPayload {
    private Neighbor neighbor;
    private ByteBuffer data;

    public PreProcessPayload(Neighbor neighbor, ByteBuffer data) {
        this.neighbor = neighbor;
        this.data = data;
    }

    public Neighbor getNeighbor() {
        return neighbor;
    }

    public void setNeighbor(Neighbor neighbor) {
        this.neighbor = neighbor;
    }

    public ByteBuffer getData() {
        return data;
    }
}
