package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.network.FIFOCache;
import com.iota.iri.network.TransactionCacheDigester;
import com.iota.iri.network.protocol.Protocol;
import com.iota.iri.utils.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * The {@link PreProcessStage} expands truncated transaction gossip payloads, computes the digest of the payload and
 * converts the transaction to its trits representation.
 */
public class PreProcessStage implements Stage {

    private static final Logger log = LoggerFactory.getLogger(PreProcessStage.class);
    private FIFOCache<Long, Hash> recentlySeenBytesCache;

    /**
     * Creates a new {@link PreProcessStage}.
     *
     * @param recentlySeenBytesCache The cache to use for checking whether a transaction is known
     */
    public PreProcessStage(FIFOCache<Long, Hash> recentlySeenBytesCache) {
        this.recentlySeenBytesCache = recentlySeenBytesCache;
    }

    /**
     * Extracts the transaction gossip payload, expands it, computes the digest and then creates a new
     * {@link ProcessingContext} to the appropriate stage. If the transaction is not known, the transaction payload is
     * also converted to its trits representation.
     * 
     * @param ctx the pre process stage {@link ProcessingContext}
     * @return a {@link ProcessingContext} which either redirects to the {@link ReplyStage} or {@link HashingStage}
     *         depending on whether the transaction is known
     */
    @Override
    public ProcessingContext process(ProcessingContext ctx) {
        PreProcessPayload payload = (PreProcessPayload) ctx.getPayload();
        ByteBuffer packetData = payload.getData();
        byte[] data = packetData.array();

        // expand received tx data
        byte[] txDataBytes = Protocol.expandTx(data);
        // copy requested tx hash
        byte[] reqHashBytes = Protocol.extractRequestedTxHash(data);

        // increment all txs count
        payload.getOriginNeighbor().getMetrics().incrAllTransactionsCount();

        // compute digest of tx bytes data
        long txDigest = TransactionCacheDigester.getDigest(txDataBytes);

        Hash receivedTxHash = recentlySeenBytesCache.get(txDigest);
        Hash requestedHash = HashFactory.TRANSACTION.create(reqHashBytes, 0,
                Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES_LENGTH);

        // log cache hit/miss ratio every 50k get()s
        if (log.isDebugEnabled()) {
            long hits = recentlySeenBytesCache.getCacheHits();
            long misses = recentlySeenBytesCache.getCacheMisses();
            if ((hits + misses) % 50000L == 0) {
                log.debug("recently seen bytes cache hit/miss ratio: {}/{}", hits, misses);
                recentlySeenBytesCache.resetCacheStats();
            }
        }

        // received tx is known, therefore we can submit to the reply stage directly.
        if (receivedTxHash != null) {
            // reply with a random tip by setting the request hash to the null hash
            requestedHash = requestedHash.equals(receivedTxHash) ? Hash.NULL_HASH : requestedHash;
            ctx.setNextStage(TransactionProcessingPipeline.Stage.REPLY);
            ctx.setPayload(new ReplyPayload(payload.getOriginNeighbor(), requestedHash));
            return ctx;
        }

        // convert tx byte data into trits representation once
        byte[] txTrits = new byte[TransactionViewModel.TRINARY_SIZE];
        Converter.getTrits(txDataBytes, txTrits);

        // submit to hashing stage.
        ctx.setNextStage(TransactionProcessingPipeline.Stage.HASHING);
        HashingPayload hashingStagePayload = new HashingPayload(payload.getOriginNeighbor(), txTrits, txDigest,
                requestedHash);
        ctx.setPayload(hashingStagePayload);
        return ctx;
    }
}
