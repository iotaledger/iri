package com.iota.iri.network.pipeline;

import com.iota.iri.TransactionValidator;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.network.FIFOCache;
import com.iota.iri.network.neighbor.Neighbor;

import static com.iota.iri.model.Hash.SIZE_IN_TRITS;

/**
 * The {@link ValidationStage} validates the given transaction, caches it as recently seen and then either submits
 * further to the {@link ReceivedStage} or/and {@link ReplyStage} depending on whether the transaction originated from a
 * neighbor or not.
 */
public class ValidationStage implements Stage {

    private TransactionValidator txValidator;
    private FIFOCache<Long, Hash> recentlySeenBytesCache;

    /**
     * Creates a new {@link ValidationStage}.
     * 
     * @param txValidator            the {@link TransactionValidator} to use to validate the transaction
     * @param recentlySeenBytesCache the {@link FIFOCache} to cache the validate transaction as recently seen
     */
    public ValidationStage(TransactionValidator txValidator, FIFOCache<Long, Hash> recentlySeenBytesCache) {
        this.txValidator = txValidator;
        this.recentlySeenBytesCache = recentlySeenBytesCache;
    }

    /**
     * Validates the transaction and caches it as 'recently seen'.
     * 
     * @param ctx the reply stage {@link ProcessingContext}
     * @return a {@link ProcessingContext} either directing to only the {@link ReceivedStage} or both
     *         {@link ReceivedStage} and {@link ReplyStage}, depending on whether the transaction came from a neighbor
     *         or not
     */
    @Override
    public ProcessingContext process(ProcessingContext ctx) {
        ValidationPayload payload = (ValidationPayload) ctx.getPayload();
        byte[] hashTrits = payload.getHashTrits();
        byte[] txTrits = payload.getTxTrits();
        Neighbor originNeighbor = payload.getOriginNeighbor();
        Long txBytesDigest = payload.getTxBytesDigest();
        Hash hashOfRequestedTx = payload.getHashOfRequestedTx();

        // construct transaction hash and model
        TransactionHash txHash = (TransactionHash) HashFactory.TRANSACTION.create(hashTrits, 0, SIZE_IN_TRITS);
        TransactionViewModel tvm = new TransactionViewModel(txTrits, txHash);

        try {
            txValidator.runValidation(tvm, txValidator.getMinWeightMagnitude());
        } catch (TransactionValidator.StaleTimestampException ex) {
            if (originNeighbor != null) {
                originNeighbor.getMetrics().incrStaleTransactionsCount();
            }
            ctx.setNextStage(TransactionProcessingPipeline.Stage.ABORT);
            return ctx;
        } catch (Exception ex) {
            if (originNeighbor != null) {
                originNeighbor.getMetrics().incrInvalidTransactionsCount();
            }
            ctx.setNextStage(TransactionProcessingPipeline.Stage.ABORT);
            return ctx;
        }

        // cache the tx hash under the tx payload digest
        if (txBytesDigest != null && txBytesDigest != 0) {
            recentlySeenBytesCache.put(txBytesDigest, txHash);
        }

        ReceivedPayload receivedStagePayload = new ReceivedPayload(originNeighbor, tvm);

        // go directly to receive stage if the transaction didn't originate from a neighbor
        if (hashOfRequestedTx == null || originNeighbor == null) {
            ctx.setNextStage(TransactionProcessingPipeline.Stage.RECEIVED);
            ctx.setPayload(receivedStagePayload);
            return ctx;
        }

        // diverge flow to received and reply stage
        ctx.setNextStage(TransactionProcessingPipeline.Stage.MULTIPLE);
        hashOfRequestedTx = hashOfRequestedTx.equals(txHash) ? Hash.NULL_HASH : hashOfRequestedTx;

        ReplyPayload replyStagePayload = new ReplyPayload(originNeighbor, hashOfRequestedTx);
        ProcessingContext replyCtx = new ProcessingContext(TransactionProcessingPipeline.Stage.REPLY,
                replyStagePayload);
        ProcessingContext receivedCtx = new ProcessingContext(TransactionProcessingPipeline.Stage.RECEIVED,
                receivedStagePayload);
        ctx.setPayload(new MultiStagePayload(replyCtx, receivedCtx));
        return ctx;
    }
}
