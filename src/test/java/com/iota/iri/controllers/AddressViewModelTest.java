package com.iota.iri.controllers;

import com.iota.iri.conf.Configuration;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProviderTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionHash;
import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionTrits;
import static org.junit.Assert.*;

/**
 * Created by paul on 5/2/17.
 */
public class AddressViewModelTest {
    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        dbFolder.create();
        logFolder.create();
        Configuration.put(Configuration.DefaultConfSettings.DB_PATH, dbFolder.getRoot().getAbsolutePath());
        Configuration.put(Configuration.DefaultConfSettings.DB_LOG_PATH, logFolder.getRoot().getAbsolutePath());
        Tangle.instance().addPersistenceProvider(RocksDBPersistenceProviderTest.rocksDBPersistenceProvider);
        Tangle.instance().init();

    }

    @After
    public void tearDown() throws Exception {
        Tangle.instance().shutdown();
        dbFolder.delete();
        logFolder.delete();
    }

    @Test
    public void getTransactionHashes() throws Exception {
        Hash address = getRandomTransactionHash();
        List<Hash> hashes = new ArrayList<>();
        int[] trits;

        for(int i = 0; i++ < 2;) {
            trits = getRandomTransactionWithAddress(address);
            hashes.add(Hash.calculate(trits));
            new TransactionViewModel(trits, hashes.get(hashes.size() - 1)).store();

            Assert.assertEquals(i, new AddressViewModel(address).getTransactionHashes().length);
        }
    }

    @Test
    public void bytes() throws Exception {
        Hash address = getRandomTransactionHash();
        Assert.assertTrue(Arrays.equals(address.bytes(), new AddressViewModel(address).bytes()));
    }

    @Test
    public void getHash() throws Exception {
        Hash address = getRandomTransactionHash();
        Assert.assertTrue(address.equals(new AddressViewModel(address).getHash()));
    }

    public static int[] getRandomTransactionWithAddress(Hash address) {
        int[] trits = getRandomTransactionTrits();
        System.arraycopy(address.trits(), 0, trits, TransactionViewModel.ADDRESS_TRINARY_OFFSET,
                TransactionViewModel.ADDRESS_TRINARY_SIZE);
        return trits;
    }
}