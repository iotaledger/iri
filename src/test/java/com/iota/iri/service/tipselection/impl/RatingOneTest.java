package com.iota.iri.service.tipselection.impl;

import static com.iota.iri.TransactionTestUtils.getTransactionHash;
import static com.iota.iri.TransactionTestUtils.getTransactionTrits;
import static com.iota.iri.TransactionTestUtils.getTransactionTritsWithTrunkAndBranch;

import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.impl.SnapshotProviderImpl;
import com.iota.iri.service.tipselection.RatingCalculator;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;

public class RatingOneTest {
    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static final String TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT =
            "tx%d cumulative weight is not as expected";
    private static Tangle tangle;
    private static SnapshotProvider snapshotProvider;
    private static RatingCalculator rating;

    @AfterClass
    public static void tearDown() throws Exception {
        tangle.shutdown();
        snapshotProvider.shutdown();
        dbFolder.delete();
    }

    @BeforeClass
    public static void setUp() throws Exception {
        tangle = new Tangle();
        snapshotProvider = new SnapshotProviderImpl().init(new MainnetConfig());
        dbFolder.create();
        logFolder.create();
        tangle.addPersistenceProvider( new RocksDBPersistenceProvider(
                dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),1000,
                Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));
        tangle.init();
        rating = new RatingOne(tangle);
    }

    @Test
    public void testCalculate() throws Exception {
        TransactionViewModel transaction, transaction1, transaction2, transaction3, transaction4;
        transaction = new TransactionViewModel(getTransactionTrits(), getTransactionHash());
        transaction1 = new TransactionViewModel(getTransactionTritsWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getTransactionHash());
        transaction2 = new TransactionViewModel(getTransactionTritsWithTrunkAndBranch(transaction1.getHash(),
                transaction1.getHash()), getTransactionHash());
        transaction3 = new TransactionViewModel(getTransactionTritsWithTrunkAndBranch(transaction2.getHash(),
                transaction1.getHash()), getTransactionHash());
        transaction4 = new TransactionViewModel(getTransactionTritsWithTrunkAndBranch(transaction2.getHash(),
                transaction3.getHash()), getTransactionHash());
        transaction.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction1.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction2.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction3.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction4.store(tangle, snapshotProvider.getInitialSnapshot());
        Map<Hash, Integer> rate = rating.calculate(transaction.getHash());

        Assert.assertEquals(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT,
                1, rate.get(transaction4.getHash()).intValue());
        Assert.assertEquals(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT,
                1, rate.get(transaction3.getHash()).intValue());
        Assert.assertEquals(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT,
                1, rate.get(transaction2.getHash()).intValue());
        Assert.assertEquals(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT,
                1, rate.get(transaction1.getHash()).intValue());
        Assert.assertEquals(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT,
                1, rate.get(transaction.getHash()).intValue());
    }
}
