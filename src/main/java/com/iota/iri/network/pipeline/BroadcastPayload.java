package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.neighbor.Neighbor;

/**
 * Defines a payload which gets submitted to the {@link BroadcastStage}.
 */
public class BroadcastPayload extends Payload {

    private Neighbor originNeighbor;
    private TransactionViewModel tvm;

    /**
     * Creates a new {@link BroadcastPayload} with the given neighbor and transaction.
     * 
     * @param originNeighbor The neighbor from which the transaction originated from
     * @param tvm            The transaction
     */
    public BroadcastPayload(Neighbor originNeighbor, TransactionViewModel tvm) {
        this.originNeighbor = originNeighbor;
        this.tvm = tvm;
    }

    /**
     * Gets the origin neighbor.
     * 
     * @return the origin neighbor
     */
    public Neighbor getOriginNeighbor() {
        return originNeighbor;
    }

    /**
     * Gets the transaction
     * 
     * @return the transaction
     */
    public TransactionViewModel getTransactionViewModel() {
        return tvm;
    }

    @Override
    public String toString() {
        return "BroadcastPayload{" + "originNeighbor=" + originNeighbor.getHostAddressAndPort() + ", tvm="
                + tvm.getHash() + '}';
    }
}
