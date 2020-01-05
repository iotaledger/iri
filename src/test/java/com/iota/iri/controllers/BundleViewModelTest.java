package com.iota.iri.controllers;

import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.impl.SnapshotMockUtils;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static com.iota.iri.TransactionTestUtils.getTransactionTrits;

public class BundleViewModelTest {
    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static Tangle tangle = new Tangle();
    private static Snapshot snapshot;

    @Before
    public void setUp() throws Exception {
        dbFolder.create();
        logFolder.create();
        RocksDBPersistenceProvider rocksDBPersistenceProvider;
        rocksDBPersistenceProvider =  new RocksDBPersistenceProvider(
                dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),1000,
                Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY);
        tangle.addPersistenceProvider(rocksDBPersistenceProvider);
        tangle.init();
        snapshot = SnapshotMockUtils.createSnapshot();
    }

    @After
    public void tearDown() throws Exception {
        tangle.shutdown();
        dbFolder.delete();
        logFolder.delete();
    }

    @Test
    public void quietFromHash() throws Exception {

    }

    @Test
    public void fromHash() throws Exception {

    }

    @Test
    public void getTransactionViewModels() throws Exception {

    }

    @Test
    public void quietGetTail() throws Exception {

    }

    @Test
    public void getTail() throws Exception {

    }

    @Test
    public void firstShouldFindTx() throws Exception {
        byte[] trits = getTransactionTrits();
        TransactionViewModel transactionViewModel = new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));
        transactionViewModel.store(tangle, snapshot);

        BundleViewModel result = BundleViewModel.first(tangle);
        Assert.assertTrue(result.getHashes().contains(transactionViewModel.getHash()));
    }

}