package com.iota.iri.service.snapshot.impl;

import static org.junit.Assert.*;

import com.iota.iri.storage.LocalSnapshotsPersistenceProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.iota.iri.conf.ConfigFactory;
import com.iota.iri.conf.IotaConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.spentaddresses.SpentAddressesException;

public class SnapshotProviderImplTest {

    private final IotaConfig iotaConfig = ConfigFactory.createIotaConfig(true);
    private SnapshotProviderImpl provider;

    private SnapshotImpl cachedBuiltinSnapshot;

    private LocalSnapshotsPersistenceProvider localSnapshotDb;

    @Before
    public void setUp() throws Exception {
        localSnapshotDb = new LocalSnapshotsPersistenceProvider();
        localSnapshotDb.init();
        provider = new SnapshotProviderImpl(iotaConfig, localSnapshotDb);
        
        // When running multiple tests, the static cached snapshot breaks this test
        cachedBuiltinSnapshot = SnapshotProviderImpl.builtinSnapshot;
        SnapshotProviderImpl.builtinSnapshot = null;
    }

    @After
    public void tearDown(){
        provider.shutdown();
        
        // Set back the cached snapshot for tests after us who might use it
        SnapshotProviderImpl.builtinSnapshot = cachedBuiltinSnapshot;
    }
    
    @Test
    public void testGetLatestSnapshot() throws SnapshotException, SpentAddressesException {
        provider.init();

        // If we run this on its own, it correctly takes the testnet milestone
        // However, running it with all tests makes it load the last global snapshot contained in the jar
        assertEquals("Initial snapshot index should be the same as the milestone start index", 
                iotaConfig.getMilestoneStartIndex(), provider.getInitialSnapshot().getIndex());
        
        assertEquals("Initial snapshot timestamp should be the same as last snapshot time", 
                iotaConfig.getSnapshotTime(), provider.getInitialSnapshot().getInitialTimestamp());

        assertEquals("Initial snapshot hash should be the genesis transaction",
                Hash.NULL_HASH, provider.getInitialSnapshot().getHash());
        
        assertEquals("Initial provider snapshot should be equal to the latest snapshot", 
                provider.getInitialSnapshot(), provider.getLatestSnapshot());
        
        assertTrue("Initial snapshot should have a filled map of addresses", provider.getInitialSnapshot().getBalances().size() > 0);
        assertTrue("Initial snapshot supply should be equal to all supply", provider.getInitialSnapshot().hasCorrectSupply());
    }
}
