package com.iota.iri.storage;

import com.iota.iri.conf.Configuration;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.controllers.TransactionViewModelTest;
import com.iota.iri.utils.Converter;
import com.iota.iri.controllers.TransactionViewModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.Random;

import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionTrits;

/**
 * Created by paul on 3/3/17 for iri.
 */
public class TangleTest {

    @Before
    public void setUp() throws Exception {
        Tangle instance;
        TemporaryFolder dbFolder = new TemporaryFolder(), logFolder = new TemporaryFolder();
        dbFolder.create();
        logFolder.create();
        Configuration.put(Configuration.DefaultConfSettings.DB_PATH, dbFolder.getRoot().getAbsolutePath());
        Configuration.put(Configuration.DefaultConfSettings.DB_LOG_PATH, logFolder.getRoot().getAbsolutePath());
        instance = Tangle.instance();
        instance.addPersistenceProvider(new RocksDBPersistenceProvider());
        instance.init();
    }

    @After
    public void tearDown() throws Exception {
        Tangle.instance().shutdown();
    }

    @Test
    public void save() throws Exception {
        Transaction transaction = new Transaction();
        Random r = new Random();
        int[] hash = new int[Curl.HASH_LENGTH],
                trits = Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE])
                        .map(i -> r.nextInt(3)-1).toArray();
        Curl curl = new Curl();
        curl.absorb(trits, 0, trits.length);
        curl.squeeze(hash, 0, Curl.HASH_LENGTH);
        transaction.bytes = Converter.bytes(trits);
        transaction.hash = new Hash(Converter.bytes(hash));

        //assertTrue("Should be a new, unique transaction", !Tangle.instance().save(transaction).get());
    }

    @Test
    public void getKeysStartingWithValue() throws Exception {
        int[] trits = getRandomTransactionTrits();
        TransactionViewModel transactionViewModel = new TransactionViewModel(trits, Hash.calculate(trits));
        transactionViewModel.store();
        Hash[] tag = Tangle.instance().keysStartingWith(Transaction.class, Arrays.copyOf(transactionViewModel.getTagValue().bytes(), 15)).get();
        //Assert.assertNotEquals(tag.length, 0);
    }

    @Test
    public void get() throws Exception {
        /*
        Transaction transaction = new Transaction();
        Random r = new Random();
        int[] hash = new int[Curl.HASH_LENGTH],
                trits = Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE])
                        .map(i -> r.nextInt(3)-1).toArray();
        Curl curl = new Curl();
        curl.absorb(trits, 0, trits.length);
        curl.squeeze(hash, 0, Curl.HASH_LENGTH);
        transaction.value = Converter.value(trits);
        byte[] byteHash = Converter.value(hash);

        transaction = (Transaction) Tangle.instance().load(Transaction.class, byteHash).get();
        assertNotNull(transaction);
        assertArrayEquals(transaction.hash, byteHash);
        */
    }

}