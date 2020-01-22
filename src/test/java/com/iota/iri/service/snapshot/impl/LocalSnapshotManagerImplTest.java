package com.iota.iri.service.snapshot.impl;

import com.iota.iri.TangleMockUtils;
import com.iota.iri.TransactionTestUtils;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.SnapshotService;
import com.iota.iri.service.snapshot.conditions.SnapshotDepthCondition;
import com.iota.iri.service.transactionpruning.TransactionPruner;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.thread.ThreadUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.exceptions.base.MockitoAssertionError;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LocalSnapshotManagerImplTest {
    
    private static final int BUFFER = LocalSnapshotManagerImpl.LOCAL_SNAPSHOT_SYNC_BUFFER;
    
    private static final int DELAY_SYNC = 5;
    private static final int DELAY_UNSYNC = 1;
    private static final int SNAPSHOT_DEPTH = 5;

    @Rule 
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private static SnapshotConfig config;
    
    
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    SnapshotProvider snapshotProvider;
    
    @Mock
    SnapshotService snapshotService;
    
    @Mock
    TransactionPruner transactionPruner;

    @Mock
    Tangle tangle;
    
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    LatestMilestoneTracker milestoneTracker;
    
    private LocalSnapshotManagerImpl lsManager;

    @Before
    public void setUp() throws Exception {
        this.lsManager = new LocalSnapshotManagerImpl(snapshotProvider, snapshotService, transactionPruner, config);
        this.lsManager.addSnapshotCondition(new SnapshotDepthCondition(config, snapshotProvider));
        
        when(snapshotProvider.getLatestSnapshot().getIndex()).thenReturn(-5, -1, 10, 998, 999, 1999, 2000);
        
        when(config.getLocalSnapshotsIntervalSynced()).thenReturn(DELAY_SYNC);
        when(config.getLocalSnapshotsIntervalUnsynced()).thenReturn(DELAY_UNSYNC);
        when(config.getLocalSnapshotsDepth()).thenReturn(SNAPSHOT_DEPTH);
    }

    @After
    public void tearDown() {
        lsManager.shutdown();
    }
    
    @Test
    public synchronized void takeLocalSnapshot() throws Exception {
        // Mock a milestone we should try to get
        TangleMockUtils.mockMilestone(tangle, TransactionTestUtils.getTransactionHash(), 100-SNAPSHOT_DEPTH-1);
        
        // Always return true
        when(milestoneTracker.isInitialScanComplete()).thenReturn(true);
        
        // When we call it, we are in sync
        when(milestoneTracker.getLatestMilestoneIndex()).thenReturn(200);
        
        // We are more then the depth ahead
        when(snapshotProvider.getLatestSnapshot().getIndex()).thenReturn(100);
        when(snapshotProvider.getInitialSnapshot().getIndex()).thenReturn(100 - SNAPSHOT_DEPTH - DELAY_SYNC - 1);
        
        // Run in separate thread to allow us to time-out
        Thread t = new Thread(() -> lsManager.monitorThread(milestoneTracker));

        t.start();
        // We should finish directly, margin for slower computers
        ThreadUtils.sleep(100);
        
        // Cancel the thread
        t.interrupt();
        
        // Verify we took a snapshot
        try {
            verify(snapshotService, times(1)).takeLocalSnapshot(any(), any(), ArgumentMatchers.anyInt());
        } catch (MockitoAssertionError e) {
            throw new MockitoAssertionError("A snapshot should have been taken when we are below SNAPSHOT_DEPTH");
        }
    }

    @Test
    public void isInSyncTestScanIncomplete() {
        when(milestoneTracker.isInitialScanComplete()).thenReturn(false);
        
        assertFalse("We should be out of sync when he havent finished initial scans", lsManager.isInSync(milestoneTracker));
    }
    
    @Test
    public void isInSyncTestScanComplete() {
        // Always return true
        when(milestoneTracker.isInitialScanComplete()).thenReturn(true);
        
        // We don't really support -1 indexes, but if this breaks, it is a good indication to be careful going further
        when(milestoneTracker.getLatestMilestoneIndex()).thenReturn(-1, 5, 10, 998 + BUFFER - 1, 2000);
        
        // snapshotProvider & milestoneTracker
        // -5 & -1 -> not in sync
        assertFalse("Previous out of sync and not equal index should not be in sync", lsManager.isInSync(milestoneTracker));
        
        // -1 and 5 -> not in sync
        assertFalse("Previous out of sync and not equal index should not be in sync", lsManager.isInSync(milestoneTracker));
        
        // 10 and 10 -> in sync
        assertTrue("Equal index should be in sync", lsManager.isInSync(milestoneTracker));
        
        // 998 and 1002 -> in sync since sync gap = 5
        assertTrue("Index difference less than the buffer still should be in sync", lsManager.isInSync(milestoneTracker));
        
        // 999 and 2000 -> out of sync again, bigger gap than 5
        assertFalse("Index difference more than the buffer should be out of sync again ", lsManager.isInSync(milestoneTracker));
        
        // 1999 and 2000 -> out of sync still
        assertFalse("Previous out of sync and not equal index should not be in sync", lsManager.isInSync(milestoneTracker));
        
        // 2000 and 2000 -> in sync again
        assertTrue("Equal index should be in sync", lsManager.isInSync(milestoneTracker));
    }
}
