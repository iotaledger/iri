package com.iota.iri.service.tipselection.impl;

import com.iota.iri.LedgerValidator;
import com.iota.iri.Milestone;
import com.iota.iri.Snapshot;
import com.iota.iri.TransactionValidator;
import com.iota.iri.conf.Configuration;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.TipsManager;
import com.iota.iri.service.tipselection.RatingCalculator;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.IotaUtils;
import com.iota.iri.zmq.MessageQ;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.Buffer;
import java.util.HashSet;
import java.util.Map;

import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionHash;
import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionTrits;
import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch;
import static org.junit.Assert.*;

public class RatingOneTest {
    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static final String TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT =
            "tx%d cumulative weight is not as expected";
    private static Tangle tangle;
    private static RatingCalculator rating;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @AfterClass
    public static void tearDown() throws Exception {
        tangle.shutdown();
        dbFolder.delete();
    }

    @BeforeClass
    public static void setUp() throws Exception {
        tangle = new Tangle();
        dbFolder.create();
        logFolder.create();
        tangle.addPersistenceProvider(new RocksDBPersistenceProvider(dbFolder.getRoot().getAbsolutePath(), logFolder
                .getRoot().getAbsolutePath(), 1000));
        tangle.init();
        rating = new RatingOne(tangle);
    }

    @Test
    public void testCalculate() throws Exception {
        TransactionViewModel transaction, transaction1, transaction2, transaction3, transaction4;
        transaction = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        transaction1 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction2 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction1.getHash(),
                transaction1.getHash()), getRandomTransactionHash());
        transaction3 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction2.getHash(),
                transaction1.getHash()), getRandomTransactionHash());
        transaction4 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction2.getHash(),
                transaction3.getHash()), getRandomTransactionHash());
        transaction.store(tangle);
        transaction1.store(tangle);
        transaction2.store(tangle);
        transaction3.store(tangle);
        transaction4.store(tangle);
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