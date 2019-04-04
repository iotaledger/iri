package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.network.FIFOCache;
import com.iota.iri.network.NeighborRouter;
import com.iota.iri.network.protocol.Protocol;
import com.iota.iri.utils.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class PreProcessStage {

    private static final Logger log = LoggerFactory.getLogger(PreProcessStage.class);

    private FIFOCache<Long, Hash> recentlySeenBytesCache;

    public PreProcessStage(FIFOCache<Long, Hash> recentlySeenBytesCache) {
        this.recentlySeenBytesCache = recentlySeenBytesCache;
    }

    public ProcessingContext process(ProcessingContext ctx) {
        PreProcessPayload payload = (PreProcessPayload) ctx.getPayload();
        ByteBuffer packetData = payload.getData();
        byte[] data = packetData.array();

        // allocate buffers for tx payload and requested tx hash
        byte[] txDataBytes = new byte[Transaction.SIZE];
        byte[] reqHashBytes = new byte[Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES];

        // expand received tx data
        Protocol.expandTx(data, txDataBytes);

        // copy requested hash
        System.arraycopy(data, data.length - Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES, reqHashBytes, 0,
                Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES);

        // compute digest of tx bytes data
        long txDigest = NeighborRouter.getTxCacheDigest(txDataBytes);

        Hash receivedTxHash = recentlySeenBytesCache.get(txDigest);
        Hash requestedHash = HashFactory.TRANSACTION.create(reqHashBytes, 0, Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES);

        // received tx is known, therefore we can submit to the reply stage directly.
        if (receivedTxHash != null) {
            // reply with a random tip by setting the request hash to the null hash
            requestedHash = requestedHash.equals(receivedTxHash) ? Hash.NULL_HASH : requestedHash;
            ctx.setNextStage(TxPipeline.Stage.REPLY);
            ctx.setPayload(new ReplyPayload(payload.getNeighbor(), requestedHash));
            return ctx;
        }

        // convert tx byte data into trits representation once
        byte[] txTrits = new byte[TransactionViewModel.TRINARY_SIZE];
        Converter.getTrits(txDataBytes, txTrits);

        // submit to hashing stage.
        ctx.setNextStage(TxPipeline.Stage.HASHING);
        HashingPayload hashingStagePayload = new HashingPayload(payload.getNeighbor(), txTrits, txDigest,
                requestedHash);
        ctx.setPayload(hashingStagePayload);
        return ctx;
    }
}
