package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.NeighborRouter;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.service.milestone.InSyncService;
import com.iota.iri.service.validation.TransactionSolidifier;

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

    private TransactionSolidifier transactionSolidifier;

    /**
     * Service used to determine if we send back tx to the original neighbour
     */
    private InSyncService inSyncService;

    /**
     * Creates a new {@link BroadcastStage}.
     * 
     * @param neighborRouter The {@link NeighborRouter} instance to use to broadcast
     */
    public BroadcastStage(NeighborRouter neighborRouter, TransactionSolidifier transactionSolidifier, InSyncService inSyncService) {
        this.neighborRouter = neighborRouter;
        this.transactionSolidifier = transactionSolidifier;
        this.inSyncService = inSyncService;
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
            
            // don't send back to origin neighbor, unless we are not in sync yet
            // Required after PR: #1745 which removes ping pong behaviour  
            if (neighbor.equals(originNeighbor) && inSyncService.isInSync()) {
                continue;
            }
            try {
                neighborRouter.gossipTransactionTo(neighbor, tvm);
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }

        // Check the transaction solidifier to see if there are solid transactions that need to be broadcast.
        // If so, forward them to the BroadcastStageQueue to be processed.
        TransactionViewModel transactionToBroadcast;
        if((transactionToBroadcast = transactionSolidifier.getNextTxInBroadcastQueue()) != null){
            ctx.setNextStage(TransactionProcessingPipeline.Stage.BROADCAST);
            ctx.setPayload(new BroadcastPayload(payload.getOriginNeighbor(), transactionToBroadcast));
            return ctx;
        }

        ctx.setNextStage(TransactionProcessingPipeline.Stage.FINISH);
        return ctx;
    }
}
