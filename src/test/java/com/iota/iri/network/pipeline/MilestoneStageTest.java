package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.validation.TransactionSolidifier;
import com.iota.iri.storage.Tangle;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

public class MilestoneStageTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Tangle tangle;

    @Mock
    private SnapshotProvider snapshotProvider;

    @Mock
    private Snapshot snapshot;

    @Mock
    private Hash milestoneHash;

    @Mock
    private TransactionViewModel milestoneTransaction;

    @Mock
    private TransactionSolidifier txSolidifier;

    @Mock
    private MilestoneSolidifier milestoneSolidifier;

    private int milestoneIndex;
    private int snapshotIndex;


    @Test
    public void newMilestoneProcessesThroughToSolidification() throws InterruptedException {
        milestoneIndex = 11;
        snapshotIndex = 10;

        when(snapshotProvider.getLatestSnapshot()).thenReturn(snapshot);
        when(milestoneSolidifier.getLatestMilestoneIndex()).thenReturn(snapshotIndex);

        MilestoneStage milestoneStage = new MilestoneStage(milestoneSolidifier, snapshotProvider, txSolidifier);
        Payload milestonePayload = new MilestonePayload(null, milestoneTransaction, milestoneIndex);
        ProcessingContext ctx = new ProcessingContext(milestonePayload);

        milestoneStage.process(ctx);
        Thread.sleep(100);

        //Milestone transaction should be placed into the MilestoneSolidifier
        verify(milestoneSolidifier, times(1)).addMilestoneCandidate(any(), anyInt());

        assertEquals("Expected next stage to be Solidify", TransactionProcessingPipeline.Stage.SOLIDIFY,
                ctx.getNextStage());
    }


    @Test
    public void existingMilestoneIsPropagated() throws Exception {
        milestoneIndex = 11;
        snapshotIndex = 11;

        when(snapshotProvider.getLatestSnapshot()).thenReturn(snapshot);
        when(milestoneSolidifier.getLatestMilestoneIndex()).thenReturn(snapshotIndex);
        when(milestoneTransaction.isSolid()).thenReturn(true);

        MilestoneStage milestoneStage = new MilestoneStage(milestoneSolidifier, snapshotProvider, txSolidifier);
        Payload milestonePayload = new MilestonePayload(null, milestoneTransaction, milestoneIndex);
        ProcessingContext ctx = new ProcessingContext(milestonePayload);

        milestoneStage.process(ctx);
        Thread.sleep(100);

        //Milestone transaction is added to Propagation
        verify(txSolidifier, times(1)).addToPropagationQueue(any());

        assertEquals("Expected next stage to be Solidify", TransactionProcessingPipeline.Stage.SOLIDIFY,
                ctx.getNextStage());
    }


    @Test
    public void newMilestoneBelowSnapshotAborts() throws InterruptedException {
        milestoneIndex = 10;
        snapshotIndex = 11;

        when(snapshotProvider.getLatestSnapshot()).thenReturn(snapshot);
        when(snapshot.getInitialIndex()).thenReturn(snapshotIndex);

        MilestoneStage milestoneStage = new MilestoneStage(milestoneSolidifier, snapshotProvider, txSolidifier);
        Payload milestonePayload = new MilestonePayload(null, milestoneTransaction, milestoneIndex);
        ProcessingContext ctx = new ProcessingContext(milestonePayload);

        milestoneStage.process(ctx);
        Thread.sleep(100);

        assertEquals("Expected process to abort", TransactionProcessingPipeline.Stage.ABORT,
                ctx.getNextStage());

    }
}
