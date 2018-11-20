package com.iota.iri.controllers;

import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

/**
 * Created by paul on 4/11/17.
 */
public class MilestoneViewModelTest {
    final TemporaryFolder dbFolder = new TemporaryFolder();
    final TemporaryFolder logFolder = new TemporaryFolder();
    private static Tangle tangle = new Tangle();
    int index = 30;

    @Before
    public void setUpTest() throws Exception {
        dbFolder.create();
        logFolder.create();
        RocksDBPersistenceProvider rocksDBPersistenceProvider;
        rocksDBPersistenceProvider = new RocksDBPersistenceProvider(dbFolder.getRoot().getAbsolutePath(),
                logFolder.getRoot().getAbsolutePath(),1000);
        tangle.addPersistenceProvider(rocksDBPersistenceProvider);
        tangle.init();
    }

    @After
    public void tearDown() throws Exception {
        tangle.shutdown();
        dbFolder.delete();
        logFolder.delete();
    }

    @Test
    public void getMilestone() throws Exception {
        Hash milestoneHash = HashFactory.TRANSACTION.create("ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999");
        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(++index, milestoneHash);
        assertTrue(milestoneViewModel.store(tangle));
        MilestoneViewModel.clear();
        MilestoneViewModel.load(tangle, index);
        assertEquals(MilestoneViewModel.get(tangle, index).getHash(), milestoneHash);
    }

    @Test
    public void store() throws Exception {
        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(++index, Hash.NULL_HASH);
        assertTrue(milestoneViewModel.store(tangle));
    }

    @Test
    public void snapshot() throws Exception {
        Hash milestoneHash = HashFactory.TRANSACTION.create("BBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999");
        long value = 3;
        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(++index, milestoneHash);
    }

    @Test
    public void initSnapshot() throws Exception {
        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(++index, Hash.NULL_HASH);
    }

    @Test
    public void updateSnapshot() throws Exception {
        Hash milestoneHash = HashFactory.TRANSACTION.create("CBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999");
        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(++index, milestoneHash);
        assertTrue(milestoneViewModel.store(tangle));
        MilestoneViewModel.clear();
        assertEquals(MilestoneViewModel.get(tangle, index).getHash(), milestoneHash);

    }

    @Test
    public void getHash() throws Exception {
        Hash milestoneHash = HashFactory.TRANSACTION.create("DBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999");
        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(++index, milestoneHash);
        assertEquals(milestoneHash, milestoneViewModel.getHash());
    }

    @Test
    public void index() throws Exception {
        Hash milestoneHash = HashFactory.TRANSACTION.create("EBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999");
        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(++index, milestoneHash);
        assertTrue(index == milestoneViewModel.index());
    }

    @Test
    public void latest() throws Exception {
        int top = 100;
        Hash milestoneHash = HashFactory.TRANSACTION.create("ZBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999");
        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(top, milestoneHash);
        milestoneViewModel.store(tangle);
        assertTrue(top == MilestoneViewModel.latest(tangle).index());
    }

    @Test
    public void first() throws Exception {
        int first = 1;
        Hash milestoneHash = HashFactory.TRANSACTION.create("99CDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999");
        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(first, milestoneHash);
        milestoneViewModel.store(tangle);
        assertTrue(first == MilestoneViewModel.first(tangle).index());
    }

    @Test
    public void next() throws Exception {
        int first = 1;
        int next = 2;
        MilestoneViewModel firstMilestone = new MilestoneViewModel(first, HashFactory.TRANSACTION.create("99CDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999"));
        firstMilestone.store(tangle);
        new MilestoneViewModel(next, HashFactory.TRANSACTION.create("9ACDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")).store(tangle);

        assertTrue(next == MilestoneViewModel.first(tangle).next(tangle).index());
    }

    @Test
    public void previous() throws Exception {
        int first = 1;
        int next = 2;
        MilestoneViewModel nextMilestone = new MilestoneViewModel(next, HashFactory.TRANSACTION.create("99CDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999"));
        nextMilestone.store(tangle);
        new MilestoneViewModel(first, HashFactory.TRANSACTION.create("9ACDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")).store(tangle);

        assertTrue(first == nextMilestone.previous(tangle).index());
    }

    @Test
    public void latestSnapshot() throws Exception {
        int nosnapshot = 90;
        int topSnapshot = 80;
        int mid = 50;
        new MilestoneViewModel(nosnapshot, HashFactory.TRANSACTION.create("FBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")).store(tangle);
        MilestoneViewModel milestoneViewModelmid = new MilestoneViewModel(mid, HashFactory.TRANSACTION.create("GBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999"));
        milestoneViewModelmid.store(tangle);
        MilestoneViewModel milestoneViewModeltopSnapshot = new MilestoneViewModel(topSnapshot, HashFactory.TRANSACTION.create("GBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999"));
        milestoneViewModeltopSnapshot.store(tangle);
        //assertTrue(topSnapshot == MilestoneViewModel.latestWithSnapshot().index());
    }

    @Test
    public void firstWithSnapshot() throws Exception {
        int first = 5;
        int firstSnapshot = 6;
        int next = 7;
        new MilestoneViewModel(first, HashFactory.TRANSACTION.create("FBCDEFGHIJ9LMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")).store(tangle);
        MilestoneViewModel milestoneViewModelmid = new MilestoneViewModel(next, HashFactory.TRANSACTION.create("GBCDE9GHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999"));
        milestoneViewModelmid.store(tangle);
        MilestoneViewModel milestoneViewModeltopSnapshot = new MilestoneViewModel(firstSnapshot, HashFactory.TRANSACTION.create("GBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYA9ABCDEFGHIJKLMNOPQRSTUV99999"));
        milestoneViewModeltopSnapshot.store(tangle);
        //assertTrue(firstSnapshot == MilestoneViewModel.firstWithSnapshot().index());
    }

    @Test
    public void nextWithSnapshot() throws Exception {
        int firstSnapshot = 8;
        int next = 9;
        MilestoneViewModel milestoneViewModelmid = new MilestoneViewModel(next, HashFactory.TRANSACTION.create("GBCDEBGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999"));
        milestoneViewModelmid.store(tangle);
        MilestoneViewModel milestoneViewModel = new MilestoneViewModel(firstSnapshot, HashFactory.TRANSACTION.create("GBCDEFGHIJKLMNODQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999"));
        milestoneViewModel.store(tangle);
        //assertTrue(next == milestoneViewModel.nextWithSnapshot().index());
    }

    @Test
    public void nextGreaterThan() throws Exception {
        int milestoneStartIndex = new MainnetConfig().getMilestoneStartIndex();
        int first = milestoneStartIndex + 1;
        int next = first + 1;
        new MilestoneViewModel(next, HashFactory.TRANSACTION.create("GBCDEBGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")).store(tangle);
        new MilestoneViewModel(first, HashFactory.TRANSACTION.create("GBCDEFGHIJKLMNODQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")).store(tangle);
        assertEquals("the found milestone should be following the previous one", next, MilestoneViewModel.findClosestNextMilestone(tangle, first, next).index().intValue());
    }

    @Test
    public void PrevBefore() throws Exception {
        int first = 8;
        int next = 9;
        new MilestoneViewModel(next, HashFactory.TRANSACTION.create("GBCDEBGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")).store(tangle);
        new MilestoneViewModel(first, HashFactory.TRANSACTION.create("GBCDEFGHIJKLMNODQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")).store(tangle);
        assertEquals(first, MilestoneViewModel.findClosestPrevMilestone(tangle, next, first).index().intValue());
    }
}
