package com.iota.iri.tangle;

import com.iota.iri.hash.Curl;
import com.iota.iri.model.Transaction;
import com.iota.iri.utils.Converter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Created by paul on 3/3/17 for iri.
 */
public class TangleAccessorTest {

    @Before
    public void setUp() throws Exception {
        TangleAccessor.instance().setPersistenceProvider(new MockPersistenceProvider());
        TangleAccessor.instance().init();
    }

    @After
    public void tearDown() throws Exception {
        TangleAccessor.instance().shutdown();
    }

    @Test
    public void init() throws Exception {
        TangleAccessor.instance().modelIndices.keySet().stream().forEach(c -> {
            assertNotEquals(TangleAccessor.instance().modelPrimaryKeys.get(c), null);
            assertNotEquals(TangleAccessor.instance().modelIndices.get(c).size(), 0);
            assertNotEquals(TangleAccessor.instance().modelStoredItems.get(c).size(), 0);
            //System.out.println(TangleAccessor.instance().modelPrimaryKeys.load(c));
            //System.out.println(Arrays.toString(TangleAccessor.instance().modelIndices.load(c).toArray()));
            //System.out.println(Arrays.toString(TangleAccessor.instance().modelStoredItems.load(c).toArray()));
        });
    }

    @Test
    public void save() throws Exception {
        Transaction transaction = new Transaction();
        Random r = new Random();
        int[] hash = new int[Curl.HASH_LENGTH],
                trits = Arrays.stream(new int[com.iota.iri.viewModel.Transaction.TRINARY_SIZE])
                        .map(i -> r.nextInt(3)-1).toArray();
        Curl curl = new Curl();
        curl.absorb(trits, 0, trits.length);
        curl.squeeze(hash, 0, Curl.HASH_LENGTH);
        transaction.bytes = Converter.bytes(trits);
        transaction.hash = Converter.bytes(hash);

        assertTrue("Should be a new, unique transaction", TangleAccessor.instance().save(transaction).get());
    }

    @Test
    public void get() throws Exception {
        Transaction transaction = new Transaction();
        Random r = new Random();
        int[] hash = new int[Curl.HASH_LENGTH],
                trits = Arrays.stream(new int[com.iota.iri.viewModel.Transaction.TRINARY_SIZE])
                        .map(i -> r.nextInt(3)-1).toArray();
        Curl curl = new Curl();
        curl.absorb(trits, 0, trits.length);
        curl.squeeze(hash, 0, Curl.HASH_LENGTH);
        transaction.bytes = Converter.bytes(trits);
        byte[] byteHash = Converter.bytes(hash);

        assertTrue("Should be a transaction type", TangleAccessor.instance().load(transaction, byteHash).get());
        assertArrayEquals(transaction.hash, byteHash);
    }

    @Test
    public void query() throws Exception {
        Transaction transaction = new Transaction();
        assertTrue("Should find the transaction",
        TangleAccessor.instance().query(transaction, "address", Converter.bytes(Converter.trits("IAMTHEBESTRANDOMADDRESSNONEARE9ETTERTHANMEDONTYOUKNOWIT"))).get());
        assertNotNull("Primary index should be populated", transaction.hash);
    }
}