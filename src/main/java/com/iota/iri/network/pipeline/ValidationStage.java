package com.iota.iri.network.pipeline;

import com.iota.iri.TransactionValidator;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.network.FIFOCache;
import com.iota.iri.network.neighbor.Neighbor;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static com.iota.iri.model.Hash.SIZE_IN_TRITS;

public class ValidationStage {

    private static final Logger log = LoggerFactory.getLogger(ValidationStage.class);

    private TransactionValidator txValidator;
    private FIFOCache<Long, Hash> recentlySeenBytesCache;

    public ValidationStage(TransactionValidator txValidator, FIFOCache<Long, Hash> recentlySeenBytesCache) {
        this.txValidator = txValidator;
        this.recentlySeenBytesCache = recentlySeenBytesCache;
    }

    public ProcessingContext process(ProcessingContext ctx) {
        ValidationPayload payload = (ValidationPayload) ctx.getPayload();
        byte[] hashTrits = payload.getHashTrits();
        byte[] txTrits = payload.getTxTrits();
        Optional<Neighbor> optNeighbor = payload.getNeighbor();
        Optional<Long> optTxDigest = payload.getTxBytesDigest();
        Optional<Hash> optHashOfRequestedTx = payload.getHashOfRequestedTx();

        // construct transaction hash and model
        TransactionHash txHash = (TransactionHash) HashFactory.TRANSACTION.create(hashTrits, 0, SIZE_IN_TRITS);
        TransactionViewModel tvm = new TransactionViewModel(txTrits, txHash);

        try {
            txValidator.runValidation(tvm, txValidator.getMinWeightMagnitude());
        } catch (TransactionValidator.StaleTimestampException ex) {
            optNeighbor.ifPresent(neighbor -> neighbor.getMetrics().incrStaleTransactionsCount());
            ctx.setNextStage(TxPipeline.Stage.ABORT);
            return ctx;
        } catch (Exception ex) {
            optNeighbor.ifPresent(neighbor -> neighbor.getMetrics().incrInvalidTransactionsCount());
            ctx.setNextStage(TxPipeline.Stage.ABORT);
            return ctx;
        }

        // cache the tx hash under the tx payload digest
        optTxDigest.ifPresent(txDigest -> {
            if (txDigest == 0) {
                return;
            }
            recentlySeenBytesCache.put(txDigest, txHash);
        });

        ReceivedPayload receivedStagePayload = new ReceivedPayload(optNeighbor.orElse(null), tvm);

        // go directly to receive stage if the transaction didn't originate from a neighbor
        if (!optHashOfRequestedTx.isPresent() || !optNeighbor.isPresent()) {
            ctx.setNextStage(TxPipeline.Stage.RECEIVED);
            ctx.setPayload(receivedStagePayload);
            return ctx;
        }

        // diverge flow to received and reply stage
        ctx.setNextStage(TxPipeline.Stage.MULTIPLE);
        Hash requestedHash = optHashOfRequestedTx.get();
        Hash txToRequest = requestedHash.equals(txHash) ? Hash.NULL_HASH : requestedHash;

        ReplyPayload replyStagePayload = new ReplyPayload(optNeighbor.get(), optHashOfRequestedTx.get());
        ProcessingContext replyCtx = new ProcessingContext(TxPipeline.Stage.REPLY, replyStagePayload);
        ProcessingContext receivedCtx = new ProcessingContext(TxPipeline.Stage.RECEIVED, receivedStagePayload);
        ctx.setPayload(new ImmutablePair<>(replyCtx, receivedCtx));
        return ctx;
    }
}
