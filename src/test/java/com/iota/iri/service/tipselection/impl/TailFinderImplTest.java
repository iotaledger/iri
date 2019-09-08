package com.iota.iri.service.tipselection.impl;

import static com.iota.iri.TransactionTestUtils.getTransactionTrits;
import static com.iota.iri.TransactionTestUtils.createBundleHead;
import static com.iota.iri.TransactionTestUtils.createTransactionWithTrunkBundleHash;
import static com.iota.iri.TransactionTestUtils.getTransactionHash;
import static com.iota.iri.TransactionTestUtils.getTransactionTritsWithTrunkAndBranch;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.impl.SnapshotMockUtils;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;

public class TailFinderImplTest {

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static Tangle tangle;
    private TailFinderImpl tailFinder;

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private static SnapshotProvider snapshotProvider;

    public TailFinderImplTest() {
        tailFinder = new TailFinderImpl(tangle);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        tangle.shutdown();
        dbFolder.delete();
        logFolder.delete();
    }

    @BeforeClass
    public static void setUp() throws Exception {
        tangle = new Tangle();
        dbFolder.create();
        logFolder.create();
        tangle.addPersistenceProvider( new RocksDBPersistenceProvider(
                dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),1000,
                Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));
        tangle.init();
    }

    @Before
    public void setUpEach() {
        Mockito.when(snapshotProvider.getInitialSnapshot()).thenReturn(SnapshotMockUtils.createSnapshot());
    }

    @Test
    public void findTailTest() throws Exception {
        TransactionViewModel txa = new TransactionViewModel(getTransactionTrits(), getTransactionHash());
        txa.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel tx2 = createBundleHead(2);
        tx2.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel tx1 = createTransactionWithTrunkBundleHash(tx2, txa.getHash());
        tx1.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel tx0 = createTransactionWithTrunkBundleHash(tx1, txa.getHash());
        tx0.store(tangle, snapshotProvider.getInitialSnapshot());

        //negative index - make sure we stop at 0
        TransactionViewModel txNeg = createTransactionWithTrunkBundleHash(tx0, txa.getHash());
        txNeg.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel txLateTail = createTransactionWithTrunkBundleHash(tx1, txa.getHash());
        txLateTail.store(tangle, snapshotProvider.getInitialSnapshot());

        Optional<Hash> tail = tailFinder.findTail(tx2.getHash());
        Assert.assertTrue("no tail was found", tail.isPresent());
        Assert.assertEquals("Expected tail not found", tx0.getHash(), tail.get());
    }


    @Test
    public void findMissingTailTest() throws Exception {
        TransactionViewModel txa = new TransactionViewModel(getTransactionTrits(), getTransactionHash());
        txa.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel tx2 = createBundleHead(2);
        tx2.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel tx1 = createTransactionWithTrunkBundleHash(tx2, txa.getHash());
        tx1.store(tangle, snapshotProvider.getInitialSnapshot());

        TransactionViewModel tx0 = new TransactionViewModel(getTransactionTritsWithTrunkAndBranch(tx1.getHash(), tx2.getHash()),
                getTransactionHash());
        tx0.store(tangle, snapshotProvider.getInitialSnapshot());

        Optional<Hash> tail = tailFinder.findTail(tx2.getHash());
        Assert.assertFalse("tail was found, but should me missing", tail.isPresent());
    }
}
