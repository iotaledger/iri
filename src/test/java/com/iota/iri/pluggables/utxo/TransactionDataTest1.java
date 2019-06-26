package com.iota.iri.pluggables.utxo;


import com.google.gson.Gson;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.localinmemorygraph.LocalInMemoryGraphProvider;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.util.*;

import static com.iota.iri.controllers.TransactionViewModel.TRYTES_SIZE;
import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionHash;

import static com.iota.iri.controllers.TransactionViewModelTest.*;

public class TransactionDataTest1 {
    private static TransactionData transactionData;
    private static Tangle tangle = new Tangle();

    @BeforeClass
    public static void setUp() throws Exception {
        final TemporaryFolder dbFolder = new TemporaryFolder();
        final TemporaryFolder logFolder = new TemporaryFolder();
        dbFolder.create();
        logFolder.create();
        RocksDBPersistenceProvider rocksDBPersistenceProvider=  new RocksDBPersistenceProvider(
                dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),1000,
                Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY);
        tangle.addPersistenceProvider(rocksDBPersistenceProvider);
        tangle.addPersistenceProvider(new LocalInMemoryGraphProvider("", tangle));
        tangle.init();

        transactionData = new TransactionData();
        transactionData.init();
        transactionData.setTangle(tangle);
    }

    @Test
    public void testPersistFixedTxns() {
        TransactionViewModel a, b, c, d;
        a = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        b = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                a.getHash()), getRandomTransactionHash());
        d = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                b.getHash()), getRandomTransactionHash());
        c = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(d.getHash(),
                b.getHash()), getRandomTransactionHash());

        List<Txn> list = transactionData.readFromStr("{\"from\":\"A\",\"to\":\"b\",\"amnt\":100}"); 
        transactionData.addTxn(list.get(0)); // persist
        transactionData.putIndex(list.get(0), a.getHash());

        list = transactionData.readFromStr("{\"from\":\"A\",\"to\":\"m\",\"amnt\":100}");
        transactionData.addTxn(list.get(0)); // persist
        transactionData.putIndex(list.get(0), a.getHash());

        list = transactionData.readFromStr("{\"from\":\"A\",\"to\":\"d\",\"amnt\":100}");
        transactionData.addTxn(list.get(0)); // persist
        transactionData.putIndex(list.get(0), a.getHash());

        list = transactionData.readFromStr("{\"from\":\"A\",\"to\":\"e\",\"amnt\":100}");
        transactionData.addTxn(list.get(0)); // persist
        transactionData.putIndex(list.get(0), a.getHash());

        list = transactionData.readFromStr("{\"from\":\"b\",\"to\":\"h\",\"amnt\":50}");
        transactionData.addTxn(list.get(0)); // persist
        transactionData.putIndex(list.get(0), b.getHash());

        list = transactionData.readFromStr("{\"from\":\"h\",\"to\":\"f\",\"amnt\":50}");
        transactionData.addTxn(list.get(0)); // not-persist
        transactionData.putIndex(list.get(0), b.getHash());

        list = transactionData.readFromStr("{\"from\":\"b\",\"to\":\"f\",\"amnt\":50}");
        transactionData.addTxn(list.get(0)); // not-persist
        transactionData.putIndex(list.get(0), b.getHash());

        list = transactionData.readFromStr("{\"from\":\"e\",\"to\":\"z\",\"amnt\":100}");
        transactionData.addTxn(list.get(0)); // persist
        transactionData.putIndex(list.get(0), b.getHash());

        list = transactionData.readFromStr("{\"from\":\"z\",\"to\":\"k\",\"amnt\":100}");
        transactionData.addTxn(list.get(0)); // not-persist
        transactionData.putIndex(list.get(0), c.getHash());

        list = transactionData.readFromStr("{\"from\":\"d\",\"to\":\"c\",\"amnt\":100}");
        transactionData.addTxn(list.get(0)); // not-persist
        transactionData.putIndex(list.get(0), c.getHash());

        list = transactionData.readFromStr("{\"from\":\"A\",\"to\":\"d\",\"amnt\":100}");
        transactionData.addTxn(list.get(0)); // not-persist
        transactionData.putIndex(list.get(0), c.getHash());

        list = transactionData.readFromStr("{\"from\":\"m\",\"to\":\"e\",\"amnt\":100}");
        transactionData.addTxn(list.get(0)); // not-persist
        transactionData.putIndex(list.get(0), c.getHash());

        list = transactionData.readFromStr("{\"from\":\"A\",\"to\":\"b\",\"amnt\":100}");
        transactionData.addTxn(list.get(0)); // not-persist
        transactionData.putIndex(list.get(0), d.getHash());

        list = transactionData.readFromStr("{\"from\":\"A\",\"to\":\"c\",\"amnt\":100}");
        transactionData.addTxn(list.get(0)); // not-persist
        transactionData.putIndex(list.get(0), d.getHash());

        list = transactionData.readFromStr("{\"from\":\"A\",\"to\":\"d\",\"amnt\":100}");
        transactionData.addTxn(list.get(0)); // not-persist
        transactionData.putIndex(list.get(0), d.getHash());

        list = transactionData.readFromStr("{\"from\":\"A\",\"to\":\"e\",\"amnt\":100}");
        transactionData.addTxn(list.get(0)); // not-persist
        transactionData.putIndex(list.get(0), d.getHash());

        List<Hash> fixed = new ArrayList<>();
        fixed.add(a.getHash());
        fixed.add(b.getHash());

        transactionData.persistFixedTxns(fixed);

        Assert.assertEquals(10, transactionData.getNumTransactions());
        Assert.assertEquals(3, transactionData.getTangleToTxnMapSize());
        Assert.assertEquals(10, transactionData.getTxnToTangleMapSize()); // should be 10 because genesis is not included
    }
}
