package com.iota.iri.network.pipeline;

import com.iota.iri.service.validation.TransactionSolidifier;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.network.neighbor.impl.NeighborMetricsImpl;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.junit.Assert.assertEquals;

public class ReceivedStageTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Tangle tangle;

    @Mock
    private TransactionSolidifier transactionSolidifier;

    @Mock
    private SnapshotProvider snapshotProvider;

    @Mock
    private TransactionRequester transactionRequester;

    @Mock
    private TransactionViewModel tvm;

    @Mock
    private Neighbor neighbor;

    @Mock
    private NeighborMetricsImpl neighborMetrics;

    @Test
    public void newlyStoredTransactionUpdatesAlsoArrivalTimeAndSender() throws Exception {
        Mockito.when(tvm.store(tangle, snapshotProvider.getInitialSnapshot())).thenReturn(true);
        Mockito.when(neighbor.getMetrics()).thenReturn(neighborMetrics);
        Mockito.when(transactionRequester.removeRecentlyRequestedTransaction(Mockito.any())).thenReturn(true);

        ReceivedStage stage = new ReceivedStage(tangle, transactionSolidifier, snapshotProvider, transactionRequester);
        ReceivedPayload receivedPayload = new ReceivedPayload(neighbor, tvm);
        ProcessingContext ctx = new ProcessingContext(null, receivedPayload);
        stage.process(ctx);

        Mockito.verify(tvm).setArrivalTime(Mockito.anyLong());
        Mockito.verify(tvm).update(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(transactionRequester).removeRecentlyRequestedTransaction(Mockito.any());
        Mockito.verify(transactionRequester).requestTrunkAndBranch(Mockito.any());
        assertEquals("should submit to broadcast stage next", TransactionProcessingPipeline.Stage.SOLIDIFY,
                ctx.getNextStage());
        SolidifyPayload solidifyPayload = (SolidifyPayload) ctx.getPayload();
        assertEquals("neighbor is still the same", neighbor, solidifyPayload.getOriginNeighbor());
        assertEquals("tvm is still the same", tvm, solidifyPayload.getTransaction());
    }

    @Test
    public void alreadyStoredTransactionDoesNoUpdates() throws Exception {
        Mockito.when(tvm.store(tangle, snapshotProvider.getInitialSnapshot())).thenReturn(false);
        Mockito.when(neighbor.getMetrics()).thenReturn(neighborMetrics);

        ReceivedStage stage = new ReceivedStage(tangle, transactionSolidifier, snapshotProvider, transactionRequester);
        ReceivedPayload receivedPayload = new ReceivedPayload(neighbor, tvm);
        ProcessingContext ctx = new ProcessingContext(null, receivedPayload);
        stage.process(ctx);

        Mockito.verify(tvm, Mockito.never()).setArrivalTime(Mockito.anyLong());
        Mockito.verify(tvm, Mockito.never()).update(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(transactionRequester).removeRecentlyRequestedTransaction(Mockito.any());
        Mockito.verify(transactionRequester, Mockito.never()).requestTrunkAndBranch(Mockito.any());
        assertEquals("should submit to broadcast stage next", TransactionProcessingPipeline.Stage.SOLIDIFY,
                ctx.getNextStage());
        SolidifyPayload solidifyPayload = (SolidifyPayload) ctx.getPayload();
        assertEquals("neighbor should still be the same", neighbor, solidifyPayload.getOriginNeighbor());
        assertEquals("tvm should still be the same", tvm, solidifyPayload.getTransaction());
    }

}