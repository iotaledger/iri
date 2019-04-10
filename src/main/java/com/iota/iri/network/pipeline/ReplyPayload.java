package com.iota.iri.network.pipeline;

import com.iota.iri.model.Hash;
import com.iota.iri.network.neighbor.Neighbor;

/**
 * Defines a payload which gets submitted to the {@link ReplyStage}.
 */
public class ReplyPayload {

    private Neighbor neighbor;
    private Hash hashOfRequestedTx;

    /**
     * Creates a new {@link ReplyStage}.
     * 
     * @param neighbor          the neighbor from which the request came from
     * @param hashOfRequestedTx the hash of the requested transaction
     */
    public ReplyPayload(Neighbor neighbor, Hash hashOfRequestedTx) {
        this.neighbor = neighbor;
        this.hashOfRequestedTx = hashOfRequestedTx;
    }

    /**
     * Gets the {@link Neighbor}.
     * 
     * @return the neighbor
     */
    public Neighbor getNeighbor() {
        return neighbor;
    }

    /**
     * Sets the {@link Neighbor}.
     * 
     * @param neighbor
     */
    public void setNeighbor(Neighbor neighbor) {
        this.neighbor = neighbor;
    }

    /**
     * Gets the hash of the requested transaction.
     * 
     * @return the hash of the requested transaction
     */
    public Hash getHashOfRequestedTx() {
        return hashOfRequestedTx;
    }
}
