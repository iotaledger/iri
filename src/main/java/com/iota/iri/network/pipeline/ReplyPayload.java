package com.iota.iri.network.pipeline;

import com.iota.iri.model.Hash;
import com.iota.iri.network.neighbor.Neighbor;

/**
 * Defines a payload which gets submitted to the {@link ReplyStage}.
 */
public class ReplyPayload extends Payload {

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
     * {@inheritDoc}
     */
    public Neighbor getOriginNeighbor() {
        return neighbor;
    }

    /**
     * Sets the {@link Neighbor}.
     * 
     * @param neighbor the neighbor to set
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

    @Override
    public String toString() {
        return "ReplyPayload{" + "neighbor=" + neighbor.getHostAddressAndPort() + ", hashOfRequestedTx="
                + hashOfRequestedTx.toString() + '}';
    }
}
