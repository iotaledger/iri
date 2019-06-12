package com.iota.iri.network.pipeline;

import com.iota.iri.TransactionValidator;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.FIFOCache;
import com.iota.iri.network.SampleTransaction;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.network.neighbor.impl.NeighborMetricsImpl;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.junit.Assert.*;

public class ValidationStageTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private TransactionValidator transactionValidator;

    @Mock
    private FIFOCache<Long, Hash> recentlySeenBytesCache;

    @Mock
    private Neighbor neighbor;

    @Mock
    private NeighborMetricsImpl neighborMetrics;

    @Test
    public void processingAValidTransactionWillProceedToReplyAndReceivedStages() {
        ValidationStage stage = new ValidationStage(transactionValidator, recentlySeenBytesCache);
        ValidationPayload validationPayload = new ValidationPayload(neighbor, SampleTransaction.TRITS_OF_SAMPLE_TX,
                SampleTransaction.CURL_HASH_OF_SAMPLE_TX.trits(), SampleTransaction.BYTES_DIGEST_OF_SAMPLE_TX,
                Hash.NULL_HASH);
        ProcessingContext ctx = new ProcessingContext(validationPayload);
        Mockito.when(transactionValidator.getMinWeightMagnitude()).thenReturn(1);

        stage.process(ctx);

        Mockito.verify(transactionValidator).runValidation(Mockito.any(TransactionViewModel.class), Mockito.anyInt());
        Mockito.verify(recentlySeenBytesCache).put(SampleTransaction.BYTES_DIGEST_OF_SAMPLE_TX,
                SampleTransaction.CURL_HASH_OF_SAMPLE_TX);

        assertEquals("should submit to multiple stages next", TransactionProcessingPipeline.Stage.MULTIPLE,
                ctx.getNextStage());
        MultiStagePayload payload = (MultiStagePayload) ctx.getPayload();
        assertEquals("should submit to reply stage next", TransactionProcessingPipeline.Stage.REPLY,
                payload.getLeft().getNextStage());
        assertEquals("should submit to received stage next", TransactionProcessingPipeline.Stage.RECEIVED,
                payload.getRight().getNextStage());
    }

    @Test
    public void processingAValidTransactionWillProceedToReceivedStageIfNoNeighborIsDefined() {
        ValidationStage stage = new ValidationStage(transactionValidator, recentlySeenBytesCache);
        ValidationPayload validationPayload = new ValidationPayload(null, SampleTransaction.TRITS_OF_SAMPLE_TX,
                SampleTransaction.CURL_HASH_OF_SAMPLE_TX.trits(), SampleTransaction.BYTES_DIGEST_OF_SAMPLE_TX,
                Hash.NULL_HASH);
        ProcessingContext ctx = new ProcessingContext(validationPayload);
        Mockito.when(transactionValidator.getMinWeightMagnitude()).thenReturn(1);

        stage.process(ctx);

        Mockito.verify(transactionValidator).runValidation(Mockito.any(TransactionViewModel.class), Mockito.anyInt());
        Mockito.verify(recentlySeenBytesCache).put(SampleTransaction.BYTES_DIGEST_OF_SAMPLE_TX,
                SampleTransaction.CURL_HASH_OF_SAMPLE_TX);

        assertEquals("should submit to received stage next", TransactionProcessingPipeline.Stage.RECEIVED,
                ctx.getNextStage());
        ReceivedPayload receivedPayload = (ReceivedPayload) ctx.getPayload();
        assertNull("neighbor should not be present", receivedPayload.getOriginNeighbor());
        assertNotNull("tvm should not be null", receivedPayload.getTransactionViewModel());
    }

    @Test
    public void processingAnInvalidTransactionWillAbortTheProcessing() {
        ValidationStage stage = new ValidationStage(transactionValidator, recentlySeenBytesCache);
        Mockito.doThrow(new IllegalStateException()).when(transactionValidator).runValidation(Mockito.any(),
                Mockito.anyInt());
        Mockito.when(neighbor.getMetrics()).thenReturn(neighborMetrics);

        ValidationPayload validationPayload = new ValidationPayload(neighbor, SampleTransaction.TRITS_OF_SAMPLE_TX,
                SampleTransaction.CURL_HASH_OF_SAMPLE_TX.trits(), SampleTransaction.BYTES_DIGEST_OF_SAMPLE_TX,
                Hash.NULL_HASH);
        ProcessingContext ctx = new ProcessingContext(validationPayload);
        Mockito.when(transactionValidator.getMinWeightMagnitude()).thenReturn(1);

        stage.process(ctx);

        Mockito.verify(transactionValidator).runValidation(Mockito.any(TransactionViewModel.class), Mockito.anyInt());
        Mockito.verify(neighborMetrics).incrInvalidTransactionsCount();
        assertEquals("should abort pipeline flow", TransactionProcessingPipeline.Stage.ABORT, ctx.getNextStage());
    }
}