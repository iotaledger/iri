package com.iota.iri.tangle.rocksDB;

import com.iota.iri.hash.Curl;
import com.iota.iri.model.ITransaction;
import com.iota.iri.tangle.IPersistenceProvider;
import com.iota.iri.tangle.TangleAccessor;
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
        int[] trytes = Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray(),
        hash = new int[Curl.HASH_LENGTH];
        ITransaction transaction = new ITransaction();
        transaction.bytes = Converter.bytes(trytes);
        Curl curl = new Curl();
        curl.absorb(trytes, 0, trytes.length);
        curl.squeeze(hash, 0, Curl.HASH_LENGTH);
        transaction.hash = Converter.bytes(hash);

        TangleAccessor.instance().getPersistenceProvider().save(transaction);
    }

    @Test
    public void get() throws Exception {

    }

    @Test
    public void query() throws Exception {

    }

}