package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.neighbor.Neighbor;

import java.util.Optional;

public class ReceivedPayload {
    private Neighbor neighbor;
    private TransactionViewModel tvm;

    public ReceivedPayload(Neighbor neighbor, TransactionViewModel tvm) {
        this.neighbor = neighbor;
        this.tvm = tvm;
    }

    public Optional<Neighbor> getNeighbor() {
        return neighbor == null ? Optional.empty() : Optional.of(neighbor);
    }

    public TransactionViewModel getTransactionViewModel() {
        return tvm;
    }
}
