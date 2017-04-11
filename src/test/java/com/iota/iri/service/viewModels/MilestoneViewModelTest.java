package com.iota.iri.service.viewModels;

import com.iota.iri.Milestone;
import com.iota.iri.Snapshot;
import com.iota.iri.conf.Configuration;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.service.tangle.rocksDB.RocksDBPersistenceProviderTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

/**
 * Created by paul on 4/11/17.
 */
public class MilestoneViewModelTest {
    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    long index = 0;

    @BeforeClass
    public static void setUp() throws Exception {
        dbFolder.create();
        logFolder.create();
        Configuration.put(Configuration.DefaultConfSettings.DB_PATH, dbFolder.getRoot().getAbsolutePath());
        Configuration.put(Configuration.DefaultConfSettings.DB_LOG_PATH, logFolder.getRoot().getAbsolutePath());
        Tangle.instance().addPersistenceProvider(RocksDBPersistenceProviderTest.rocksDBPersistenceProvider);
        Tangle.instance().init();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        Tangle.instance().shutdown();
        dbFolder.delete();
    }

    @Test
    public void getMilestone() throws Exception {
        Hash milestoneHash = new Hash("ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999");
        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(++index, milestoneHash);
        milestoneViewModel.initSnapshot(Snapshot.initialState);
        assertTrue(milestoneViewModel.store());
        MilestoneViewModel.clear();
        MilestoneViewModel.load(index);
        assertEquals(MilestoneViewModel.get(index).getHash(), milestoneHash);
        assertEquals(MilestoneViewModel.get(index).snapshot(), Snapshot.initialState);
    }

    @Test
    public void store() throws Exception {
        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(++index, Hash.NULL_HASH);
        assertTrue(milestoneViewModel.store());
    }

    @Test
    public void snapshot() throws Exception {
        Hash milestoneHash = new Hash("BBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999");
        long value = 3;
        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(++index, milestoneHash);
        milestoneViewModel.initSnapshot(null);
        milestoneViewModel.snapshot().put(Hash.NULL_HASH, value);
        assertEquals(1, milestoneViewModel.snapshot().size());
    }

    @Test
    public void initSnapshot() throws Exception {
        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(++index, Hash.NULL_HASH);
        assertNull(milestoneViewModel.snapshot());
        milestoneViewModel.initSnapshot(Snapshot.initialState);
        assertEquals(milestoneViewModel.snapshot(), Snapshot.initialState);
    }

    @Test
    public void updateSnapshot() throws Exception {
        Hash milestoneHash = new Hash("CBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999");
        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(++index, milestoneHash);
        assertTrue(milestoneViewModel.store());
        milestoneViewModel.initSnapshot(Snapshot.initialState);
        milestoneViewModel.updateSnapshot();
        MilestoneViewModel.clear();
        assertEquals(MilestoneViewModel.get(index).getHash(), milestoneHash);
        assertEquals(MilestoneViewModel.get(index).snapshot(), Snapshot.initialState);

    }

    @Test
    public void getHash() throws Exception {
        Hash milestoneHash = new Hash("DBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999");
        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(++index, milestoneHash);
        assertEquals(milestoneHash, milestoneViewModel.getHash());
    }

    @Test
    public void index() throws Exception {
        Hash milestoneHash = new Hash("EBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999");
        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(++index, milestoneHash);
        assertEquals(index, milestoneViewModel.index());
    }

    @Test
    public void latest() throws Exception {
        int top = 100;
        Hash milestoneHash = new Hash("ZBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999");
        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(top, milestoneHash);
        milestoneViewModel.store();
        assertEquals(top, MilestoneViewModel.latest().index());
    }

    @Test
    public void latestSnapshot() throws Exception {
        int nosnapshot = 90;
        int topSnapshot = 80;
        int mid = 50;
        new MilestoneViewModel(nosnapshot, new Hash("FBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")).store();
        MilestoneViewModel milestoneViewModelmid = new MilestoneViewModel(mid, new Hash("GBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999"));
        milestoneViewModelmid.initSnapshot(Snapshot.initialState);
        milestoneViewModelmid.store();
        MilestoneViewModel milestoneViewModeltopSnapshot = new MilestoneViewModel(topSnapshot, new Hash("GBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999"));
        milestoneViewModeltopSnapshot.initSnapshot(Snapshot.initialState);
        milestoneViewModeltopSnapshot.store();
        assertEquals(topSnapshot, MilestoneViewModel.latestWithSnapshot().index());
    }
}