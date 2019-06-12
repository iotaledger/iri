package com.iota.iri.network.pipeline;

import com.iota.iri.TransactionValidator;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ReceivedStage} stores the given transaction in the database, updates the arrival time and sender and then
 * submits to the {@link BroadcastStage}.
 */
public class ReceivedStage implements Stage {

    private static final Logger log = LoggerFactory.getLogger(ReceivedStage.class);

    private Tangle tangle;
    private TransactionValidator txValidator;
    private SnapshotProvider snapshotProvider;

    /**
     * Creates a new {@link ReceivedStage}.
     * 
     * @param tangle           The {@link Tangle} database used to store/update the transaction
     * @param txValidator      The {@link TransactionValidator} used to store/update the transaction
     * @param snapshotProvider The {@link SnapshotProvider} used to store/update the transaction
     */
    public ReceivedStage(Tangle tangle, TransactionValidator txValidator, SnapshotProvider snapshotProvider) {
        this.txValidator = txValidator;
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
    }

    /**
     * Stores the given transaction in the database, updates it status
     * ({@link TransactionValidator#updateStatus(TransactionViewModel)}) and updates the sender.
     * 
     * @param ctx the received stage {@link ProcessingContext}
     * @return a {@link ProcessingContext} which redirects to the {@link BroadcastStage}
     */
    public ProcessingContext process(ProcessingContext ctx) {
        ReceivedPayload payload = (ReceivedPayload) ctx.getPayload();
        Neighbor originNeighbor = payload.getOriginNeighbor();
        TransactionViewModel tvm = payload.getTransactionViewModel();

        boolean stored;
        try {
            stored = tvm.store(tangle, snapshotProvider.getInitialSnapshot());
        } catch (Exception e) {
            log.error("error persisting newly received tx", e);
            if (originNeighbor != null) {
                originNeighbor.getMetrics().incrInvalidTransactionsCount();
            }
            ctx.setNextStage(TransactionProcessingPipeline.Stage.ABORT);
            return ctx;
        }

        if (stored) {
            tvm.setArrivalTime(System.currentTimeMillis());
            try {
                txValidator.updateStatus(tvm);
                // neighbor might be null because tx came from a broadcastTransaction command
                if (originNeighbor != null) {
                    tvm.updateSender(originNeighbor.getHostAddressAndPort());
                }
                tvm.update(tangle, snapshotProvider.getInitialSnapshot(), "arrivalTime|sender");
            } catch (Exception e) {
                log.error("error updating newly received tx", e);
            }
            if (originNeighbor != null) {
                originNeighbor.getMetrics().incrNewTransactionsCount();
            }
        }

        // broadcast the newly saved tx to the other neighbors
        ctx.setNextStage(TransactionProcessingPipeline.Stage.BROADCAST);
        ctx.setPayload(new BroadcastPayload(originNeighbor, tvm));
        return ctx;
    }
}
