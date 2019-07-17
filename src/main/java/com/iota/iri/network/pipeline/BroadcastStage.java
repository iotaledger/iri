package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.NeighborRouter;
import com.iota.iri.network.neighbor.Neighbor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * The {@link BroadcastStage} takes care of broadcasting newly received transactions to all neighbors except the
 * neighbor from which the transaction originated from.
 */
public class BroadcastStage implements Stage {

    private static final Logger log = LoggerFactory.getLogger(BroadcastStage.class);

    private NeighborRouter neighborRouter;

    /**
     * Creates a new {@link BroadcastStage}.
     * 
     * @param neighborRouter The {@link NeighborRouter} instance to use to broadcast
     */
    public BroadcastStage(NeighborRouter neighborRouter) {
        this.neighborRouter = neighborRouter;
    }

    /**
     * Extracts the transaction and then broadcasts it to all neighbors. If the transaction originated from a neighbor,
     * it is not sent to that given neighbor.
     * 
     * @param ctx the broadcast stage {@link ProcessingContext}
     * @return the same ctx as passed in
     */
    @Override
    public ProcessingContext process(ProcessingContext ctx) {
        BroadcastPayload payload = (BroadcastPayload) ctx.getPayload();
        Neighbor originNeighbor = payload.getOriginNeighbor();
        TransactionViewModel tvm = payload.getTransactionViewModel();

        // racy
        Map<String, Neighbor> currentlyConnectedNeighbors = neighborRouter.getConnectedNeighbors();
        for (Neighbor neighbor : currentlyConnectedNeighbors.values()) {
            // don't send back to origin neighbor
            if (neighbor.equals(originNeighbor)) {
                continue;
            }
            try {
                neighborRouter.gossipTransactionTo(neighbor, tvm);
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }

        ctx.setNextStage(TransactionProcessingPipeline.Stage.FINISH);
        return ctx;
    }
}
