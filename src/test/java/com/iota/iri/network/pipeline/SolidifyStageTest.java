package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.service.validation.TransactionSolidifier;
import com.iota.iri.storage.Tangle;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.junit.Assert.assertEquals;

public class SolidifyStageTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Tangle tangle;

    @Mock
    private TipsViewModel tipsViewModel;

    @Mock
    private TransactionViewModel tvm;

    @Mock
    private Hash originalHash;

    @Mock
    private Hash tipHash;

    @Mock
    private TransactionSolidifier transactionSolidifier;

    @Test
    public void solidTransactionIsBroadcast() throws Exception{
        Mockito.when(tvm.isSolid()).thenReturn(true);
        Mockito.when(tvm.getHash()).thenReturn(originalHash);

        SolidifyStage solidifyStage = new SolidifyStage(transactionSolidifier, tipsViewModel, tangle);
        SolidifyPayload solidifyPayload = new SolidifyPayload(null, tvm);
        ProcessingContext ctx = new ProcessingContext(solidifyPayload);

        solidifyStage.process(ctx);
        Thread.sleep(100);

        assertEquals("Expected next stage to be broadcast", ctx.getNextStage(),
                TransactionProcessingPipeline.Stage.BROADCAST);
        BroadcastPayload broadcastPayload = (BroadcastPayload) ctx.getPayload();
        assertEquals("Expected payload hash to equal the original transaction hash",
                broadcastPayload.getTransactionViewModel().getHash(), originalHash);
    }

    @Test
    public void quickSetSolidTransactionIsBroadcast() throws Exception{
        Mockito.when(transactionSolidifier.quickSetSolid(tvm)).thenReturn(true);
        Mockito.when(tvm.getHash()).thenReturn(originalHash);

        SolidifyStage solidifyStage = new SolidifyStage(transactionSolidifier, tipsViewModel, tangle);
        SolidifyPayload solidifyPayload = new SolidifyPayload(null, tvm);
        ProcessingContext ctx = new ProcessingContext(solidifyPayload);

        solidifyStage.process(ctx);
        Thread.sleep(100);

        assertEquals("Expected next stage to be broadcast", ctx.getNextStage(),
                TransactionProcessingPipeline.Stage.BROADCAST);
        BroadcastPayload broadcastPayload = (BroadcastPayload) ctx.getPayload();
        assertEquals("Expected payload hash to equal the original transaction hash",
                broadcastPayload.getTransactionViewModel().getHash(), originalHash);
    }

    @Test
    public void unsolidTransactionBroadcastsRandomSolidTip() throws Exception{
        Mockito.when(tvm.isSolid()).thenReturn(false);
        Mockito.when(transactionSolidifier.quickSetSolid(tvm)).thenReturn(false);
        TransactionViewModel tip = new TransactionViewModel(new Transaction(), tipHash);

        SolidifyStage solidifyStage = new SolidifyStage(transactionSolidifier, tipsViewModel, tangle);
        SolidifyPayload solidifyPayload = new SolidifyPayload(null, tvm);
        ProcessingContext ctx = new ProcessingContext(solidifyPayload);

        solidifyStage.injectTip(tip);
        solidifyStage.process(ctx);
        Thread.sleep(100);

        assertEquals("Expected next stage to be broadcast", ctx.getNextStage(),
                TransactionProcessingPipeline.Stage.BROADCAST);
        BroadcastPayload broadcastPayload = (BroadcastPayload) ctx.getPayload();
        assertEquals("Expected payload hash to equal random tip hash",
                broadcastPayload.getTransactionViewModel().getHash(), tipHash);
    }

    @Test
    public void unsolidWithNoRandomTipsAborts() throws Exception{
        Mockito.when(tvm.isSolid()).thenReturn(false);
        Mockito.when(transactionSolidifier.quickSetSolid(tvm)).thenReturn(false);
        Mockito.when(tipsViewModel.getRandomSolidTipHash()).thenReturn(null);

        SolidifyStage solidifyStage = new SolidifyStage(transactionSolidifier, tipsViewModel, tangle);
        SolidifyPayload solidifyPayload = new SolidifyPayload(null, tvm);
        ProcessingContext ctx = new ProcessingContext(solidifyPayload);

        solidifyStage.process(ctx);
        Thread.sleep(100);

        assertEquals("Expected next stage to be broadcast", ctx.getNextStage(),
                TransactionProcessingPipeline.Stage.FINISH);
    }
}
