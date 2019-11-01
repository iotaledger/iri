package com.iota.iri.service.snapshot.conditions;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;

public class SnapshotConditionsTest {
    
    @Rule 
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private static MainnetConfig config;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    SnapshotProvider snapshotProvider;

    @Mock
    Tangle tangle;
    
    @Before
    public void setUp() throws Exception {
        when(config.getLocalSnapshotsIntervalSynced()).thenReturn(BaseIotaConfig.Defaults.LOCAL_SNAPSHOTS_INTERVAL_SYNCED);
        when(config.getLocalSnapshotsIntervalUnsynced()).thenReturn(BaseIotaConfig.Defaults.LOCAL_SNAPSHOTS_INTERVAL_UNSYNCED);
        
    }
    
    @Test
    public void getDelayTest() {
        SnapshotDepthCondition condition = new SnapshotDepthCondition(tangle, config, snapshotProvider);
        
        assertEquals("Out of sync should return the config value at getLocalSnapshotsIntervalUnsynced", 
                BaseIotaConfig.Defaults.LOCAL_SNAPSHOTS_INTERVAL_UNSYNCED, condition.getSnapshotInterval(false));
        
        assertEquals("In sync should return the config value at getLocalSnapshotsIntervalSynced", 
                BaseIotaConfig.Defaults.LOCAL_SNAPSHOTS_INTERVAL_SYNCED, condition.getSnapshotInterval(true));
    }
    
    @Test
    public void getDbSizeTest() {

        when(config.getDbMaxSize()).thenReturn("-1", "5kb", "10GB", "99999999999999999999TB", "5 kb", "10 gB");
        
        SnapshotSizeCondition condition = new SnapshotSizeCondition(tangle, config, snapshotProvider);
        
        
    }
}
