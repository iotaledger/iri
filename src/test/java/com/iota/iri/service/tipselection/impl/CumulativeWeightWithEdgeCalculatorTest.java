package com.iota.iri.service.tipselection.impl;

import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionHash;
import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionTrits;
import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.HashId;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.collections.interfaces.UnIterableMap;

// Some description goes here

public class CumulativeWeightWithEdgeCalculatorTest {
    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static final String TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT =
            "tx%d cumulative weight is not as expected";
    private static Tangle tangle;
    private static CumulativeWeightWithEdgeCalculator cumulativeWeightWithEdgeCalculator;

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
        tangle.addPersistenceProvider(new RocksDBPersistenceProvider(dbFolder.getRoot().getAbsolutePath(), logFolder
                .getRoot().getAbsolutePath(), 1000, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));
        tangle.init();
        cumulativeWeightWithEdgeCalculator = new CumulativeWeightWithEdgeCalculator(tangle);
    }


    @Test
    public void testCalculateCumulativeAndEdgeWeight() throws Exception {
        TransactionViewModel transaction, transaction1, transaction2, transaction3, transaction4, transaction5;
        transaction = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        transaction1 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction2 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction1.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction3 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction2.getHash(),
                transaction1.getHash()), getRandomTransactionHash());
        transaction4 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction1.getHash(),
                transaction3.getHash()), getRandomTransactionHash());
        transaction5 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction4.getHash(),
                transaction2.getHash()), getRandomTransactionHash());
        long arriveTime = System.currentTimeMillis();
        transaction.setArrivalTime(arriveTime);
        transaction.store(tangle);
        arriveTime += 200;
        transaction1.setArrivalTime(arriveTime);
        transaction1.store(tangle);
        arriveTime += 300;
        transaction2.setArrivalTime(arriveTime);
        transaction2.store(tangle);
        arriveTime += 400;
        transaction3.setArrivalTime(arriveTime);
        transaction3.store(tangle);
        arriveTime += 200;
        transaction4.setArrivalTime(arriveTime);
        transaction4.store(tangle);
        arriveTime += 400;
        transaction5.setArrivalTime(arriveTime);
        transaction5.store(tangle);
        UnIterableMap<HashId, Integer> txToCw = cumulativeWeightWithEdgeCalculator.calculate(transaction.getHash());


        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 5),
                1, txToCw.get(transaction5.getHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 4),
                1, txToCw.get(transaction4.getHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 3),
                1, txToCw.get(transaction3.getHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 2),
                3, txToCw.get(transaction2.getHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 1),
                4, txToCw.get(transaction1.getHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 0),
                3, txToCw.get(transaction.getHash()).intValue());
    }

}
