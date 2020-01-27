package com.iota.iri.service.transactionpruning.conditions;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.persistables.Milestone;
import com.iota.iri.service.transactionpruning.SizePruningCondition;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rocksdb.util.SizeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class SizePruningConditionTest {
    
    @Rule 
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private static MainnetConfig config;

    @Mock
    Tangle tangle;
    
    SizePruningCondition condition;
    
    @Before
    public void setUp() throws Exception {
        when(config.getLocalSnapshotsIntervalSynced()).thenReturn(BaseIotaConfig.Defaults.LOCAL_SNAPSHOTS_INTERVAL_SYNCED);
        when(config.getLocalSnapshotsIntervalUnsynced()).thenReturn(BaseIotaConfig.Defaults.LOCAL_SNAPSHOTS_INTERVAL_UNSYNCED);

        when(config.getLocalSnapshotsEnabled()).thenReturn(true);
        when(config.getLocalSnapshotsPruningEnabled()).thenReturn(true);
        
        when(config.getLocalSnapshotsDbMaxSize()).thenReturn("10GB");

        condition = new SizePruningCondition(tangle, config);
    }
    
    @Test
    public void getDbSizeTest() {
        when(tangle.getPersistanceSize()).thenReturn(SizeUnit.GB * 8, SizeUnit.GB * 20);
        
        assertFalse("Should not have allowed a snapshot", condition.shouldPrune());
        assertTrue("Should have allowed a snapshot", condition.shouldPrune());
    }
    
    @Test
    public void getMilestoneTest() throws Exception {
        when(tangle.getFirst(Milestone.class, IntegerIndex.class)).thenReturn(
                new Pair<>(new IntegerIndex(5), new Milestone()));
        
        
        assertEquals("Starting milestone should be " + SizePruningCondition.MILESTONES + " above the initial index", 5 + SizePruningCondition.MILESTONES,
                condition.getSnapshotPruningMilestone());
    }
}
