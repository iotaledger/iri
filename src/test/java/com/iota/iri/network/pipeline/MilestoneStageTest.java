package com.iota.iri.network.pipeline;

import com.iota.iri.model.Hash;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
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
    private TransactionSolidifier txSolidifier;

    @Mock
    private MilestoneSolidifier milestoneSolidifier;

    @Mock
    private LatestMilestoneTracker latestMilestoneTracker;

    private int milestoneIndex;
    private int snapshotIndex;


    @Test
    public void newMilestoneProcessesThroughToSolidification() throws InterruptedException {
        milestoneIndex = 11;
        snapshotIndex = 10;

        when(snapshotProvider.getLatestSnapshot()).thenReturn(snapshot);
        when(latestMilestoneTracker.getLatestMilestoneIndex()).thenReturn(snapshotIndex);

        MilestoneStage milestoneStage = new MilestoneStage(tangle, milestoneSolidifier, snapshotProvider, txSolidifier,
                                                            latestMilestoneTracker);
        Payload milestonePayload = new MilestonePayload(null, milestoneHash, milestoneIndex);
        ProcessingContext ctx = new ProcessingContext(milestonePayload);

        milestoneStage.process(ctx);
        Thread.sleep(100);

        //Milestone transaction should be updated in LatestMilestoneTracker and then placed into the MilestoneSolidifier
        verify(latestMilestoneTracker, times(1)).setLatestMilestone(any(), anyInt());
        verify(milestoneSolidifier, times(1)).add(any(), anyInt());

        assertEquals("Expected next stage to be Solidify", TransactionProcessingPipeline.Stage.SOLIDIFY,
                ctx.getNextStage());
    }


    @Test
    public void existingMilestoneIsPropagated() throws InterruptedException {
        milestoneIndex = 11;
        snapshotIndex = 11;

        when(snapshotProvider.getLatestSnapshot()).thenReturn(snapshot);
        when(latestMilestoneTracker.getLatestMilestoneIndex()).thenReturn(snapshotIndex);
        when(txSolidifier.addMilestoneToSolidificationQueue(any())).thenReturn(true);

        MilestoneStage milestoneStage = new MilestoneStage(tangle, milestoneSolidifier, snapshotProvider, txSolidifier,
                latestMilestoneTracker);
        Payload milestonePayload = new MilestonePayload(null, milestoneHash, milestoneIndex);
        ProcessingContext ctx = new ProcessingContext(milestonePayload);

        milestoneStage.process(ctx);
        Thread.sleep(100);

        //Milestone is not logged, and the transaction is added to Propagation
        verify(latestMilestoneTracker, never()).setLatestMilestone(any(), anyInt());
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

        MilestoneStage milestoneStage = new MilestoneStage(tangle, milestoneSolidifier, snapshotProvider, txSolidifier,
                latestMilestoneTracker);
        Payload milestonePayload = new MilestonePayload(null, milestoneHash, milestoneIndex);
        ProcessingContext ctx = new ProcessingContext(milestonePayload);

        milestoneStage.process(ctx);
        Thread.sleep(100);

        assertEquals("Expected process to abort", TransactionProcessingPipeline.Stage.ABORT,
                ctx.getNextStage());

    }
}
