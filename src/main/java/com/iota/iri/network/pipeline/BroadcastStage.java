package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.NeighborRouter;
import com.iota.iri.network.neighbor.Neighbor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

public class BroadcastStage {

    private static final Logger log = LoggerFactory.getLogger(BroadcastStage.class);

    private NeighborRouter neighborRouter;

    public BroadcastStage(NeighborRouter neighborRouter) {
        this.neighborRouter = neighborRouter;
    }

    public void process(ProcessingContext ctx) {
        BroadcastPayload payload = (BroadcastPayload) ctx.getPayload();
        Optional<Neighbor> optOriginNeighbor = payload.getOriginNeighbor();
        TransactionViewModel tvm = payload.getTransactionViewModel();

        // racy
        Map<String, Neighbor> currentlyConnectedNeighbors = neighborRouter.getConnectedNeighbors();
        for (Neighbor neighbor : currentlyConnectedNeighbors.values()) {
            // don't send back to origin neighbor
            if (optOriginNeighbor.isPresent() && neighbor.equals(optOriginNeighbor.get())) {
                continue;
            }
            try {
                neighborRouter.gossipTransactionTo(neighbor, tvm);
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }
}
