package com.iota.iri.service.snapshot.impl;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.SnapshotMetaData;

public class SnapshotMetaDataImplTest {

    private static final Hash A = TransactionTestUtils.getTransactionHash();
    private static final Hash B = TransactionTestUtils.getTransactionHash();
    private static final Hash C = TransactionTestUtils.getTransactionHash();
    private static final Hash D = TransactionTestUtils.getTransactionHash();
    
    private static Map<Hash, Integer> solidEntryPoints = new HashMap<Hash, Integer>(){{
        put(A, 1);
        put(B, 2);
        put(C, -1);
    }};
    
    private static Map<Hash, Integer> seenMilestones = new HashMap<Hash, Integer>(){{
        put(A, 10);
        put(B, 11);
        put(C, 12);
        put(D, 13);
    }};
    
    private SnapshotMetaDataImpl meta;

    @Before
    public void setUp() {
        meta = new SnapshotMetaDataImpl(A, 
                BaseIotaConfig.Defaults.MILESTONE_START_INDEX, 
                BaseIotaConfig.Defaults.SNAPSHOT_TIME,
                solidEntryPoints, 
                seenMilestones);
    }
    
    @Test
    public void initialIndexTest(){
        assertEquals("Initial index should be equal to the one provided", 
                meta.getInitialIndex(), BaseIotaConfig.Defaults.MILESTONE_START_INDEX);
        assertEquals("Current index should be equal to the initial index", 
                meta.getIndex(), BaseIotaConfig.Defaults.MILESTONE_START_INDEX);
        
        meta.setIndex(BaseIotaConfig.Defaults.MILESTONE_START_INDEX + 1);
        assertNotEquals("Initial index should not be the same as current index after setting", 
                meta.getInitialIndex(), meta.getIndex());
    }
    
    @Test
    public void initialTimestampTest(){
        assertEquals("Initial timestamp should be equal to the one provided", 
                meta.getInitialTimestamp(), BaseIotaConfig.Defaults.SNAPSHOT_TIME);
        assertEquals("Current timestamp should be equal to the initial timestamp", 
                meta.getTimestamp(), BaseIotaConfig.Defaults.SNAPSHOT_TIME);
        
        meta.setTimestamp(BaseIotaConfig.Defaults.SNAPSHOT_TIME + 1);
        assertNotEquals("Initial timestamp should not be the same as current timestamp after setting", 
                meta.getInitialTimestamp(), meta.getTimestamp());
    }
    
    @Test
    public void hashTest(){
        assertEquals("Initial hash should be equal to the one provided", meta.getInitialHash(), A);
        
        assertEquals("Current hash should be equal to the initial hash", meta.getHash(), A);
        
        meta.setHash(B);
        assertNotEquals("Initial hash should not be the same as current hash after setting", meta.getInitialHash(), meta.getHash());
    }
    
    @Test
    public void solidEntryPointsTest(){
        assertTrue("We should have the entry point provided on start", meta.hasSolidEntryPoint(A));
        assertTrue("We should have the entry point provided on start", meta.hasSolidEntryPoint(B));
        assertTrue("We should have the entry point provided on start", meta.hasSolidEntryPoint(C));
        
        assertEquals("Index from entry should be to the one set to the hash", 1, meta.getSolidEntryPointIndex(A));
        assertEquals("Index from entry should be to the one set to the hash", 2, meta.getSolidEntryPointIndex(B));
        
        // Test -1 to ensure, if we ever enforce this positive, something could break 
        // We don't really support -1 indexes, but if this breaks, it is a good indication to be careful going further
        assertEquals("Index from entry should be to the one set to the hash", -1, meta.getSolidEntryPointIndex(C));
        
        assertEquals("Solid entries amount should be the same as the ones provided", meta.getSolidEntryPoints().size(), solidEntryPoints.size());
        assertEquals("Solid entries should be the same as the ones provided", meta.getSolidEntryPoints(), new HashMap<>(solidEntryPoints));
        
        meta.setSolidEntryPoints(seenMilestones);
        // Right now, the map is replaced, so none are 'new' or 'existing'.  
        assertEquals("Existing entrypoints should have a new index", 10, meta.getSolidEntryPointIndex(A));
        assertEquals("Existing entrypoints should have a new index", 11, meta.getSolidEntryPointIndex(B));
        assertEquals("Existing entrypoints should have a new index", 12, meta.getSolidEntryPointIndex(C));
        assertEquals("New entry point added should exist and equal to the index provided", 13, meta.getSolidEntryPointIndex(D));
    }
    
    @Test
    public void seenMilestonesTest(){
        assertEquals("Seen milestones amount should be the same as the ones provided", 
                meta.getSeenMilestones().size(), seenMilestones.size());
        assertEquals("Seen milestones should be the same as the ones provided", 
                meta.getSeenMilestones(), new HashMap<>(seenMilestones));
    }
    
    @Test
    public void createTest(){
        SnapshotMetaData newMetaData = new SnapshotMetaDataImpl(meta);
        assertEquals("new SnapshotMetaData made from another should be equal", newMetaData, meta);
        
        newMetaData = new SnapshotMetaDataImpl(Hash.NULL_HASH, 0, 0l, Collections.EMPTY_MAP, Collections.EMPTY_MAP);
        newMetaData.update(meta);
        assertEquals("Updating a SnapshotMetaData with another should make them equal", newMetaData, meta);
    }
    
}
