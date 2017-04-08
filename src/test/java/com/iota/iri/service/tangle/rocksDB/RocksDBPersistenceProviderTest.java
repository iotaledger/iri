package com.iota.iri.service.tangle.rocksDB;

import com.iota.iri.conf.Configuration;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.service.viewModels.TransactionViewModel;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;

import static com.iota.iri.service.viewModels.TransactionViewModelTest.getRandomTransactionTrits;

/**
 * Created by paul on 3/4/17 for iri.
 */
public class RocksDBPersistenceProviderTest {
    public static final RocksDBPersistenceProvider rocksDBPersistenceProvider = new RocksDBPersistenceProvider();

    @BeforeClass
    public static void setUp() throws Exception {
        TemporaryFolder dbFolder = new TemporaryFolder(), logFolder = new TemporaryFolder();
        dbFolder.create();
        logFolder.create();
        Configuration.put(Configuration.DefaultConfSettings.DB_PATH, dbFolder.getRoot().getAbsolutePath());
        Configuration.put(Configuration.DefaultConfSettings.DB_LOG_PATH, logFolder.getRoot().getAbsolutePath());
        Tangle.instance().addPersistenceProvider(rocksDBPersistenceProvider);
        Tangle.instance().init();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        Tangle.instance().shutdown();
    }

    @Test
    public void find() throws Exception {
        TransactionViewModel transactionViewModel = new TransactionViewModel(getRandomTransactionTrits());
        transactionViewModel.store();
        Hash hash = transactionViewModel.getHash();
        Transaction transaction = ((Transaction) rocksDBPersistenceProvider.seek(Transaction.class, Arrays.copyOf(hash.bytes(), TransactionViewModel.HASH_SIZE)));
        Assert.assertArrayEquals(transaction.bytes, transactionViewModel.getBytes());
    }
}