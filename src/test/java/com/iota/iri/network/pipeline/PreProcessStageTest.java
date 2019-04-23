package com.iota.iri.network.pipeline;

import com.iota.iri.model.Hash;
import com.iota.iri.network.FIFOCache;
import com.iota.iri.network.SampleTransaction;
import com.iota.iri.network.neighbor.impl.NeighborImpl;
import com.iota.iri.network.protocol.Protocol;

import java.nio.ByteBuffer;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class PreProcessStageTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private FIFOCache<Long, Hash> recentlySeenBytesCache;

    @Mock
    private NeighborImpl neighbor;

    @Test
    public void processingAnUnknownTxDirectsToHashingStage() {
        PreProcessStage stage = new PreProcessStage(recentlySeenBytesCache);
        ByteBuffer rawTxGossipData = SampleTransaction.createSampleTxBuffer();
        PreProcessPayload payload = new PreProcessPayload(neighbor, rawTxGossipData);
        ProcessingContext ctx = new ProcessingContext(null, payload);
        stage.process(ctx);

        // the cache should be checked for the digest of the transaction
        Mockito.verify(recentlySeenBytesCache).get(SampleTransaction.BYTES_DIGEST_OF_SAMPLE_TX);

        assertEquals(TransactionProcessingPipeline.Stage.HASHING, ctx.getNextStage());
        HashingPayload hashingPayload = (HashingPayload) ctx.getPayload();

        assertEquals(neighbor, hashingPayload.getNeighbor().get());
        assertEquals(SampleTransaction.BYTES_DIGEST_OF_SAMPLE_TX, hashingPayload.getTxBytesDigest().get().longValue());
        assertArrayEquals(SampleTransaction.TRITS_OF_SAMPLE_TX, hashingPayload.getTxTrits());
        assertEquals(Hash.NULL_HASH, hashingPayload.getHashOfRequestedTx().get());
    }

    @Test
    public void processingAKnownTxDirectsToReplyStage() {
        PreProcessStage stage = new PreProcessStage(recentlySeenBytesCache);
        ByteBuffer rawTxGossipData = SampleTransaction.createSampleTxBuffer();
        PreProcessPayload payload = new PreProcessPayload(neighbor, rawTxGossipData);
        ProcessingContext ctx = new ProcessingContext(null, payload);

        // make the cache know the sample tx data
        Mockito.when(recentlySeenBytesCache.get(SampleTransaction.BYTES_DIGEST_OF_SAMPLE_TX))
                .thenReturn(Hash.NULL_HASH);

        stage.process(ctx);
        assertEquals(TransactionProcessingPipeline.Stage.REPLY, ctx.getNextStage());
        ReplyPayload replyPayload = (ReplyPayload) ctx.getPayload();
        assertEquals(neighbor, replyPayload.getNeighbor());
        assertEquals(Hash.NULL_HASH, replyPayload.getHashOfRequestedTx());
    }

    @Test
    public void theTransactionsPayloadGetsExpanded() {
        PreProcessStage stage = new PreProcessStage(recentlySeenBytesCache);
        ByteBuffer truncatedTxGossipData = ByteBuffer
                .allocate(SampleTransaction.TRUNCATED_SAMPLE_TX_BYTES.length + Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES_LENGTH);
        truncatedTxGossipData.put(SampleTransaction.TRUNCATED_SAMPLE_TX_BYTES);
        truncatedTxGossipData.put(new byte[Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES_LENGTH]);
        truncatedTxGossipData.flip();

        // process the truncated transaction payload
        PreProcessPayload payload = new PreProcessPayload(neighbor, truncatedTxGossipData);
        ProcessingContext ctx = new ProcessingContext(null, payload);
        stage.process(ctx);

        assertEquals(TransactionProcessingPipeline.Stage.HASHING, ctx.getNextStage());
        HashingPayload hashingPayload = (HashingPayload) ctx.getPayload();
        assertEquals(SampleTransaction.BYTES_DIGEST_OF_SAMPLE_TX, hashingPayload.getTxBytesDigest().get().longValue());
        assertArrayEquals(SampleTransaction.TRITS_OF_SAMPLE_TX, hashingPayload.getTxTrits());
        assertEquals(Hash.NULL_HASH, hashingPayload.getHashOfRequestedTx().get());
    }
}