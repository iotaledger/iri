package com.iota.iri.network.pipeline;

import com.iota.iri.network.neighbor.Neighbor;

import java.nio.ByteBuffer;

/**
 * Defines the payload which gets submitted to the {@link PreProcessStage}.
 */
public class PreProcessPayload {

    private Neighbor neighbor;
    private ByteBuffer data;

    /**
     * Creates a new {@link PreProcessPayload}.
     * 
     * @param neighbor The origin neighbor
     * @param data     The gossip transaction data
     */
    public PreProcessPayload(Neighbor neighbor, ByteBuffer data) {
        this.neighbor = neighbor;
        this.data = data;
    }

    /**
     * Gets the {@link Neighbor}
     * 
     * @return the {@link Neighbor}
     */
    public Neighbor getNeighbor() {
        return neighbor;
    }

    /**
     * Sets the {@link Neighbor}
     * 
     * @param neighbor the {@link Neighbor}
     */
    public void setNeighbor(Neighbor neighbor) {
        this.neighbor = neighbor;
    }

    /**
     * Gets the transaction gossip data.
     * 
     * @return the transaction gossip data
     */
    public ByteBuffer getData() {
        return data;
    }
}
