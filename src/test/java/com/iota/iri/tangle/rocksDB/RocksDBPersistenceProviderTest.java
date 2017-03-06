package com.iota.iri.tangle.rocksDB;

import com.iota.iri.hash.Curl;
import com.iota.iri.model.Transaction;
import com.iota.iri.tangle.TangleAccessor;
import com.iota.iri.utils.Converter;
import com.iota.iri.viewModel.TransactionVM;
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
        TangleAccessor.instance().setPersistenceProvider(new RocksDBPersistenceProvider());
        TangleAccessor.instance().init();
    }

    @After
    public void tearDown() throws Exception {
        TangleAccessor.instance().shutdown();
    }

    @Test
    public void save() throws Exception {
        Random r = new Random();
        int[] trits = Arrays.stream(new int[TransactionVM.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray(),
        hash = new int[Curl.HASH_LENGTH];
        Transaction transaction = new Transaction();
        transaction.bytes = Converter.bytes(trits);
        Curl curl = new Curl();
        curl.absorb(trits, 0, trits.length);
        curl.squeeze(hash, 0, Curl.HASH_LENGTH);
        transaction.hash = Converter.bytes(hash);

        TangleAccessor.instance().getPersistenceProvider().save(transaction);
    }

    @Test
    public void get() throws Exception {
        Random r = new Random();
        int[] trits = Arrays.stream(new int[TransactionVM.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray(),
                hash = new int[Curl.HASH_LENGTH];
        Transaction transaction = new Transaction();
        transaction.bytes = Converter.bytes(trits);
        Curl curl = new Curl();
        curl.absorb(trits, 0, trits.length);
        curl.squeeze(hash, 0, Curl.HASH_LENGTH);
        transaction.hash = Converter.bytes(hash);

        TangleAccessor.instance().getPersistenceProvider().save(transaction);
        Transaction getTransaction = new Transaction();
        TangleAccessor.instance().getPersistenceProvider().get(getTransaction, transaction.hash);
        assertArrayEquals(getTransaction.hash, transaction.hash);
        assertArrayEquals(getTransaction.bytes, transaction.bytes);
    }

    @Test
    public void query() throws Exception {
        Random r = new Random();
        int[] trits = Arrays.stream(new int[TransactionVM.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray(),
                hash = new int[Curl.HASH_LENGTH];
        Transaction transaction = new Transaction(), queryTransaction = new Transaction();
        transaction.bytes = Converter.bytes(trits);
        transaction.address = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
        Curl curl = new Curl();
        curl.absorb(trits, 0, trits.length);
        curl.squeeze(hash, 0, Curl.HASH_LENGTH);
        transaction.hash = Converter.bytes(hash);

        TangleAccessor.instance().getPersistenceProvider().save(transaction);
        TangleAccessor.instance().getPersistenceProvider().query(queryTransaction, "address", transaction.address);

        assertArrayEquals(queryTransaction.hash, transaction.hash);
        assertArrayEquals(queryTransaction.bytes, transaction.bytes);
        assertArrayEquals(queryTransaction.address, transaction.address);
    }

}