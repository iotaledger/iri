package com.iota.iri.service.snapshot.impl;

import static org.junit.Assert.*;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.LocalSnapshot;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.storage.LocalSnapshotsPersistenceProvider;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.iota.iri.conf.ConfigFactory;
import com.iota.iri.conf.IotaConfig;
import com.iota.iri.service.snapshot.SnapshotException;
import org.junit.rules.TemporaryFolder;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.LinkedHashMap;
import java.util.Map;

public class SnapshotProviderImplTest {
    private final TemporaryFolder dbFolder = new TemporaryFolder();
    private final TemporaryFolder logFolder = new TemporaryFolder();

    private final IotaConfig iotaConfig = ConfigFactory.createIotaConfig(true);
    private SnapshotProviderImpl provider;

    private SnapshotImpl cachedBuiltinSnapshot;

    private Map<Hash, Long> insertBalances = new LinkedHashMap<Hash, Long>(){{
        put(HashFactory.ADDRESS.create("A"), 1000L);
        put(HashFactory.ADDRESS.create("B"), -1000L);
        put(HashFactory.ADDRESS.create("C"), TransactionViewModel.SUPPLY);
        put(Hash.NULL_HASH, -TransactionViewModel.SUPPLY);
    }};


    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private LocalSnapshotsPersistenceProvider localSnapshotDb;

    @Before
    public void setUp() throws Exception {
        dbFolder.create();
        logFolder.create();

        localSnapshotDb = new LocalSnapshotsPersistenceProvider(
                new RocksDBPersistenceProvider(
                        dbFolder.getRoot().getAbsolutePath(),
                        logFolder.getRoot().getAbsolutePath(),
                        1000,
                        LocalSnapshotsPersistenceProvider.COLUMN_FAMILIES,
                        null));
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
    public void testGetLatestSnapshot() throws SnapshotException {
        try{
            provider.init();
        } catch(Exception e){
            throw new SnapshotException(e);
        }
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

    @Test
    public void testPersistSnapshot() throws Exception {
        // Create a snapshot that we will persist
        Snapshot snapshot = SnapshotMockUtils.createSnapshot(100, HashFactory.TRANSACTION.create("MILESTONE"));
        snapshot.applyStateDiff(new SnapshotStateDiffImpl(insertBalances));

        try{
            provider.init();
        } catch(Exception e){
            throw new SnapshotException(e);
        }
        // Store snapshot
        provider.persistSnapshot(snapshot);

        // Fetch snapshot
        LocalSnapshot loadedSnapshot = (LocalSnapshot) localSnapshotDb.get(LocalSnapshot.class, new IntegerIndex(1));

        assertEquals("Expected loaded snapshot hash to match stored snapshot hash",
                snapshot.getHash().toString(), loadedSnapshot.milestoneHash.toString());

        assertEquals("Expected loaded snapshot index to match stored snapshot index",
                snapshot.getIndex(), loadedSnapshot.milestoneIndex);

        assertEquals("Expected loaded snapshot ledger state to match stored snapshot ledger state",
                snapshot.getBalances(), loadedSnapshot.ledgerState);
    }
}
