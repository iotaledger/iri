package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.neighbor.Neighbor;

public class SolidifyPayload extends Payload {
    Neighbor originNeighbor;
    TransactionViewModel tvm;

    public SolidifyPayload(Neighbor originNeighbor, TransactionViewModel tvm){
        this.originNeighbor = originNeighbor;
        this.tvm = tvm;
    }


    @Override
    public Neighbor getOriginNeighbor(){
        return originNeighbor;
    }

    public TransactionViewModel getTransaction(){
        return tvm;
    }
}
