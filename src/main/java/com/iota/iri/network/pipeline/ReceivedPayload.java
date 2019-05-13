package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.neighbor.Neighbor;

import java.util.Optional;

/**
 * Defines a payload which gets submitted to the {@link ReceivedStage}.
 */
public class ReceivedPayload {

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
     * Gets the {@link Neighbor}.
     * 
     * @return the neighbor
     */
    public Optional<Neighbor> getNeighbor() {
        return neighbor == null ? Optional.empty() : Optional.of(neighbor);
    }

    /**
     * Gets the transaction.
     * 
     * @return the transaction
     */
    public TransactionViewModel getTransactionViewModel() {
        return tvm;
    }
}