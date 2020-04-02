package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.neighbor.Neighbor;

/**
 * Defines the payload which gets submitted to the {@link QuickBundleValidationStage}
 */
public class QuickBundleValidationPayload extends Payload {

    private Neighbor neighbor;
    private TransactionViewModel tvm;

    /**
     * Creates a new {@link QuickBundleValidationPayload}
     *
     * @param neighbor The {@link Neighbor} form which the transaction originated from.
     * @param tvm      The transaction
     */
    public QuickBundleValidationPayload(Neighbor neighbor, TransactionViewModel tvm) {
        this.neighbor = neighbor;
        this.tvm = tvm;
    }

    @Override
    public Neighbor getOriginNeighbor() {
        return neighbor;
    }

    /**
     * Gets the transaction
     * 
     * @return The transaction
     */
    public TransactionViewModel getTransactionViewModel() {
        return tvm;
    }

    @Override
    public String toString() {
        return "QuickBundleValidationPayload{" + "neighbor=" + neighbor.getHostAddressAndPort() + ", tvm="
                + tvm.getHash() + '}';
    }

}
