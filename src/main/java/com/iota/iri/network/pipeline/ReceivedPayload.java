package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.neighbor.Neighbor;

/**
 * Defines a payload which gets submitted to the {@link ReceivedStage}.
 */
public class ReceivedPayload extends Payload {

    private Neighbor neighbor;
    private TransactionViewModel tvm;

    /**
     * Creates a new {@link ReceivedPayload}.
     * 
     * @param neighbor the {@link Neighbor} from which the transaction originated from (can be null)
     * @param tvm      the transaction
     */
    public ReceivedPayload(Neighbor neighbor, TransactionViewModel tvm) {
        this.neighbor = neighbor;
        this.tvm = tvm;
    }

    /**
     * {@inheritDoc}
     */
    public Neighbor getOriginNeighbor() {
        return neighbor;
    }

    /**
     * Gets the transaction.
     * 
     * @return the transaction
     */
    public TransactionViewModel getTransactionViewModel() {
        return tvm;
    }

    @Override
    public String toString() {
        return "ReceivedPayload{" + "neighbor=" + neighbor.getHostAddressAndPort() + ", tvm=" + tvm.getHash() + '}';
    }
}
