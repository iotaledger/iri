package com.iota.iri.service.snapshot.impl;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.iota.iri.conf.ConfigFactory;
import com.iota.iri.conf.IotaConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.spentaddresses.SpentAddressesException;

public class SnapshotProviderImplTest {

    SnapshotProviderImpl provider;
    
    @Before
    public void setUp() throws Exception {
        provider = new SnapshotProviderImpl();
    }

    @After
    public void tearDown() throws Exception {
        provider.shutdown();
    }
    
    @Test
    public void testGetLatestSnapshot() throws SnapshotException, SpentAddressesException {
        IotaConfig iotaConfig = ConfigFactory.createIotaConfig(true);
        provider.init(iotaConfig);

        assertEquals("Initial snapshot index should be the same as the milestone start index", 
                iotaConfig.getMilestoneStartIndex(), provider.getInitialSnapshot().getIndex());
        
        assertEquals("Initial snapshot timestamp should be the same as last snapshot time", 
                iotaConfig.getSnapshotTime(), provider.getInitialSnapshot().getInitialTimestamp());
        
        assertEquals("Initial snapshot hash should be the genisis transaction", 
                Hash.NULL_HASH, provider.getInitialSnapshot().getHash());
        
        assertEquals("Initial provider snapshot should be equal to the latest snapshot", 
                provider.getInitialSnapshot(), provider.getLatestSnapshot());
        
        assertTrue("Initial snapshot should have a filled map of addresses", provider.getInitialSnapshot().getBalances().size() > 0);
        assertTrue("Initial snapshot supply should be equal to all supply", provider.getInitialSnapshot().hasCorrectSupply());
    }
}
