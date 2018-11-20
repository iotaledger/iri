package com.iota.iri.service.tipselection.impl;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.controllers.TransactionViewModelTest;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.impl.SnapshotProviderImpl;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Optional;

public class TailFinderImplTest {

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static Tangle tangle;
    private static SnapshotProvider snapshotProvider;
    private TailFinderImpl tailFinder;

    public TailFinderImplTest() {
        tailFinder = new TailFinderImpl(tangle);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        tangle.shutdown();
        snapshotProvider.shutdown();
        dbFolder.delete();
        logFolder.delete();
    }

    @BeforeClass
    public static void setUp() throws Exception {
        tangle = new Tangle();
        snapshotProvider = new SnapshotProviderImpl().init(new MainnetConfig());
        dbFolder.create();
        logFolder.create();
        tangle.addPersistenceProvider(new RocksDBPersistenceProvider(dbFolder.getRoot().getAbsolutePath(), logFolder
                .getRoot().getAbsolutePath(), 1000));
        tangle.init();
    }

    @Test
    public void findTailTest() throws Exception {
        TransactionViewModel txa = new TransactionViewModel(TransactionViewModelTest.getRandomTransactionTrits(), TransactionViewModelTest.getRandomTransactionHash());
        txa.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel tx2 = TransactionTestUtils.createBundleHead(2);
        tx2.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel tx1 = TransactionTestUtils.createTransactionWithTrunkBundleHash(tx2, txa.getHash());
        tx1.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel tx0 = TransactionTestUtils.createTransactionWithTrunkBundleHash(tx1, txa.getHash());
        tx0.store(tangle, snapshotProvider.getInitialSnapshot());

        //negative index - make sure we stop at 0
        TransactionViewModel txNeg = TransactionTestUtils.createTransactionWithTrunkBundleHash(tx0, txa.getHash());
        txNeg.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel txLateTail = TransactionTestUtils.createTransactionWithTrunkBundleHash(tx1, txa.getHash());
        txLateTail.store(tangle, snapshotProvider.getInitialSnapshot());

        Optional<Hash> tail = tailFinder.findTail(tx2.getHash());
        Assert.assertTrue("no tail was found", tail.isPresent());
        Assert.assertEquals("Expected tail not found", tx0.getHash(), tail.get());
    }


    @Test
    public void findMissingTailTest() throws Exception {
        TransactionViewModel txa = new TransactionViewModel(TransactionViewModelTest.getRandomTransactionTrits(),
                TransactionViewModelTest.getRandomTransactionHash());
        txa.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel tx2 = TransactionTestUtils.createBundleHead(2);
        tx2.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel tx1 = TransactionTestUtils.createTransactionWithTrunkBundleHash(tx2, txa.getHash());
        tx1.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel tx0 = new TransactionViewModel(TransactionViewModelTest
                .getRandomTransactionWithTrunkAndBranch(tx1.getHash(), tx2.getHash()),
                TransactionViewModelTest.getRandomTransactionHash());
        tx0.store(tangle, snapshotProvider.getInitialSnapshot());

        Optional<Hash> tail = tailFinder.findTail(tx2.getHash());
        Assert.assertFalse("tail was found, but should me missing", tail.isPresent());
    }
}
