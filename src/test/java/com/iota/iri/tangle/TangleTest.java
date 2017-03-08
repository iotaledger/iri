package com.iota.iri.tangle;

import com.iota.iri.hash.Curl;
import com.iota.iri.model.Transaction;
import com.iota.iri.utils.Converter;
import com.iota.iri.viewModel.TransactionViewModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Created by paul on 3/3/17 for iri.
 */
public class TangleTest {

    @Before
    public void setUp() throws Exception {
        Tangle.instance().addPersistenceProvider(new MockPersistenceProvider());
        Tangle.instance().init();
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
        transaction.hash = Converter.bytes(hash);

        assertTrue("Should be a new, unique transaction", Tangle.instance().save(transaction).get());
    }

    @Test
    public void get() throws Exception {
        Transaction transaction = new Transaction();
        Random r = new Random();
        int[] hash = new int[Curl.HASH_LENGTH],
                trits = Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE])
                        .map(i -> r.nextInt(3)-1).toArray();
        Curl curl = new Curl();
        curl.absorb(trits, 0, trits.length);
        curl.squeeze(hash, 0, Curl.HASH_LENGTH);
        transaction.bytes = Converter.bytes(trits);
        byte[] byteHash = Converter.bytes(hash);

        assertTrue("Should be a transaction type", Tangle.instance().load(transaction, byteHash).get());
        assertArrayEquals(transaction.hash, byteHash);
    }

    @Test
    public void query() throws Exception {
        Transaction transaction = ((Transaction) Tangle.instance().query(Transaction.class, "address", Converter.bytes(Converter.trits("IAMTHEBESTRANDOMADDRESSNONEARE9ETTERTHANMEDONTYOUKNOWIT")), 0).get()[0]);
        assertNotNull("Primary index should be populated", transaction.hash);
    }
}