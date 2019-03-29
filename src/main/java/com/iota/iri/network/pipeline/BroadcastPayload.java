package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.neighbor.Neighbor;

import java.util.Optional;

public class BroadcastPayload {
    private Neighbor originNeighbor;
    private TransactionViewModel tvm;

    public BroadcastPayload(Neighbor originNeighbor, TransactionViewModel tvm) {
        this.originNeighbor = originNeighbor;
        this.tvm = tvm;
    }

    public Optional<Neighbor> getOriginNeighbor() {
        return originNeighbor == null ? Optional.empty() : Optional.of(originNeighbor);
    }

    public TransactionViewModel getTransactionViewModel() {
        return tvm;
    }
}
