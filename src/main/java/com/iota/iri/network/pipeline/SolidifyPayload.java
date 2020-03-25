package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.neighbor.Neighbor;

/**
 * Defines a payload which gets submitted to the {@link SolidifyStage}.
 */
public class SolidifyPayload extends Payload {
    private Neighbor originNeighbor;
    private TransactionViewModel tvm;

    /**
     * Constructor for solidification payload.
     *
     * @param originNeighbor    The originating point of a received transaction
     * @param tvm               The transaction that needs to be solidified
     */
    public SolidifyPayload(Neighbor originNeighbor, TransactionViewModel tvm){
        this.originNeighbor = originNeighbor;
        this.tvm = tvm;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Neighbor getOriginNeighbor(){
        return originNeighbor;
    }

    /**
     * Fetches the transaction from the payload.
     * @return          The transaction stored in the payload.
     */
    public TransactionViewModel getTransaction(){
        return tvm;
    }
}
