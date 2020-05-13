package com.iota.iri.service.milestone.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.iota.iri.service.milestone.InSyncService;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.snapshot.SnapshotProvider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class MilestoneInSyncServiceTest {
    
    private static final int BUFFER = 5;

    @Rule 
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    
    InSyncService inSyncService;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    SnapshotProvider snapshotProvider;
    
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    MilestoneSolidifier milestoneSolidifier;
    
    @Before
    public void setUp() throws Exception {
        inSyncService = new MilestoneInSyncService(snapshotProvider, milestoneSolidifier);
    }
    
    @Test
    public void isInSyncTestScanIncomplete() {
        when(milestoneSolidifier.isInitialScanComplete()).thenReturn(false);
        
        assertFalse("We should be out of sync when he havent finished initial scans", inSyncService.isInSync());
    }
    
    @Test
    public void isInSyncTestScanComplete() {
        // Always return true
        when(milestoneSolidifier.isInitialScanComplete()).thenReturn(true);
        
        // We don't really support -1 indexes, but if this breaks, it is a good indication to be careful going further
        when(milestoneSolidifier.getLatestMilestoneIndex()).thenReturn(-1, 5, 10, 998 + BUFFER - 1, 2000);
        when(snapshotProvider.getLatestSnapshot().getIndex()).thenReturn(-5, -1, 10, 998, 999, 1999, 2000);
        
        
        // snapshotProvider & milestoneTracker
        // -5 & -1 -> not in sync
        assertFalse("Previous out of sync and not equal index should not be in sync", inSyncService.isInSync());
        
        // -1 and 5 -> not in sync
        assertFalse("Previous out of sync and not equal index should not be in sync", inSyncService.isInSync());
        
        // 10 and 10 -> in sync
        assertTrue("Equal index should be in sync", inSyncService.isInSync());
        
        // 998 and 1002 -> in sync since sync gap = 5
        assertTrue("Index difference less than the buffer still should be in sync", inSyncService.isInSync());
        
        // 999 and 2000 -> out of sync again, bigger gap than 5
        assertFalse("Index difference more than the buffer should be out of sync again ", inSyncService.isInSync());
        
        // 1999 and 2000 -> out of sync still
        assertFalse("Previous out of sync and not equal index should not be in sync", inSyncService.isInSync());
        
        // 2000 and 2000 -> in sync again
        assertTrue("Equal index should be in sync", inSyncService.isInSync());
    }
}
