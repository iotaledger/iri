package com.iota.iri.service.snapshot.conditions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rocksdb.util.SizeUnit;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;

public class SnapshotConditionsTest {
    
    @Mock
    private Snapshot snapshot;
    
    @Rule 
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private static MainnetConfig config;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    SnapshotProvider snapshotProvider;

    @Mock
    Tangle tangle;
    
    SnapshotSizeCondition condition;
    
    @Before
    public void setUp() throws Exception {
        when(config.getLocalSnapshotsIntervalSynced()).thenReturn(BaseIotaConfig.Defaults.LOCAL_SNAPSHOTS_INTERVAL_SYNCED);
        when(config.getLocalSnapshotsIntervalUnsynced()).thenReturn(BaseIotaConfig.Defaults.LOCAL_SNAPSHOTS_INTERVAL_UNSYNCED);

        when(config.getLocalSnapshotsEnabled()).thenReturn(true);
        when(config.getLocalSnapshotsPruningEnabled()).thenReturn(true);
        
        when(config.getLocalSnapshotsDbMaxSize()).thenReturn("10GB");

        condition = new SnapshotSizeCondition(tangle, config, snapshotProvider);
    }
    
    @Test
    public void getDbSizeTest() {
        when(tangle.getPersistanceSize()).thenReturn(SizeUnit.GB * 8, SizeUnit.GB * 20);
        
        assertFalse("Should not have allowed a snapshot", condition.shouldTakeSnapshot(true));
        assertTrue("Should have allowed a snapshot", condition.shouldTakeSnapshot(true));
    }
    
    @Test
    public void getMilestoneTest() throws SnapshotException {
        when(snapshot.getIndex()).thenReturn(5);
        when(snapshotProvider.getInitialSnapshot()).thenReturn(snapshot);
        
        
        assertEquals("Starting milestoen should be 5 above the initial index", 10, condition.getSnapshotPruningMilestone());
    }
}
