package com.iota.iri.tangle.rocksDB;

import com.iota.iri.hash.Curl;
import com.iota.iri.model.Transaction;
import com.iota.iri.tangle.Tangle;
import com.iota.iri.utils.Converter;
import com.iota.iri.viewModel.TransactionViewModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Created by paul on 3/4/17 for iri.
 */
public class RocksDBPersistenceProviderTest {
    @Before
    public void setUp() throws Exception {
        Tangle.instance().addPersistenceProvider(new RocksDBPersistenceProvider());
        Tangle.instance().init();
    }

    @After
    public void tearDown() throws Exception {
        Tangle.instance().shutdown();
    }

    @Test
    public void save() throws Exception {
        Random r = new Random();
        int[] trits = Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray(),
        hash = new int[Curl.HASH_LENGTH];
        Transaction transaction = new Transaction();
        transaction.bytes = Converter.bytes(trits);
        Curl curl = new Curl();
        curl.absorb(trits, 0, trits.length);
        curl.squeeze(hash, 0, Curl.HASH_LENGTH);
        transaction.hash = Converter.bytes(hash);

        Tangle.instance().getPersistenceProviders().get(0).save(transaction);
    }

    @Test
    public void get() throws Exception {
        Random r = new Random();
        int[] trits = Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray(),
                hash = new int[Curl.HASH_LENGTH];
        Transaction transaction = new Transaction();
        transaction.bytes = Converter.bytes(trits);
        Curl curl = new Curl();
        curl.absorb(trits, 0, trits.length);
        curl.squeeze(hash, 0, Curl.HASH_LENGTH);
        transaction.hash = Converter.bytes(hash);

        Tangle.instance().getPersistenceProviders().get(0).save(transaction);
        Transaction getTransaction = new Transaction();
        Tangle.instance().getPersistenceProviders().get(0).get(getTransaction, transaction.hash);
        assertArrayEquals(getTransaction.hash, transaction.hash);
        assertArrayEquals(getTransaction.bytes, transaction.bytes);
    }

    @Test
    public void query() throws Exception {
        Random r = new Random();
        int[] trits = Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray(),
                hash = new int[Curl.HASH_LENGTH];
        Transaction transaction = new Transaction(), queryTransaction;
        transaction.bytes = Converter.bytes(trits);
        transaction.address.bytes = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
        Curl curl = new Curl();
        curl.absorb(trits, 0, trits.length);
        curl.squeeze(hash, 0, Curl.HASH_LENGTH);
        transaction.hash = Converter.bytes(hash);

        Tangle.instance().getPersistenceProviders().get(0).save(transaction);
        queryTransaction = Arrays.stream(
                Arrays.stream(
                        Tangle.instance()
                                .query(Transaction.class, "address", transaction.address, TransactionViewModel.BUNDLE_SIZE)
                                .get())
                        .toArray(Transaction[]::new))
                .findFirst()
                .orElse(null);
        assertNotNull(queryTransaction);
        assertArrayEquals(queryTransaction.hash, transaction.hash);
        assertArrayEquals(queryTransaction.bytes, transaction.bytes);
        assertArrayEquals(queryTransaction.address.bytes, transaction.address.bytes);
    }

    @Test
    public void queryManyTest() throws Exception {
        Random r = new Random();
        byte[] bundleHash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
        Transaction[] bundle, transactions;
        transactions = Arrays.stream(new Transaction[4]).map(t -> {
            Transaction transaction = new Transaction();
            transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
            transaction.bundle.hash = bundleHash.clone();
            transaction.hash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
            return transaction;
        }).toArray(Transaction[]::new);
        transactions[0].hash = bundleHash.clone();

        for(Transaction transaction: transactions) {
            Tangle.instance().getPersistenceProviders().get(0).save(transaction);
        }
        Object[] queryOutput = Tangle.instance()
                        .query(Transaction.class, "bundle", transactions[0].bundle, bundleHash.length)
                        .get();
        bundle = Arrays.stream(queryOutput).toArray(Transaction[]::new);
        //bundle = Arrays.stream(Tangle.instance().getPersistenceProviders().get(0).queryMany(Transaction.class, "bundle", bundleHash, bundleHash.length)).toArray(Transaction[]::new);
        assertEquals(bundle.length, transactions.length);
    }

}