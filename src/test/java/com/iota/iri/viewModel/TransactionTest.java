package com.iota.iri.viewModel;

import com.iota.iri.hash.Curl;
import com.iota.iri.tangle.TangleAccessor;
import com.iota.iri.tangle.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Created by paul on 3/5/17 for iri.
 */
public class TransactionTest {
    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void getBundleTransactions() throws Exception {
        TangleAccessor.instance().setPersistenceProvider(new RocksDBPersistenceProvider());
        TangleAccessor.instance().init();
        Random r = new Random();
        byte[] bundleHash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
        Transaction[] bundle, transactions;
        transactions = Arrays.stream(new com.iota.iri.model.Transaction[4]).map(t -> {
            com.iota.iri.model.Transaction transaction = new com.iota.iri.model.Transaction();
            transaction.bundle = bundleHash.clone();
            transaction.bytes = Converter.bytes(Arrays.stream(new int[com.iota.iri.viewModel.Transaction.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
            transaction.hash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
            return new Transaction(transaction);
        }).toArray(Transaction[]::new);
        {
            com.iota.iri.model.Transaction transaction = new com.iota.iri.model.Transaction();
            transaction.bundle = bundleHash.clone();
            transaction.bytes = Converter.bytes(Arrays.stream(new int[com.iota.iri.viewModel.Transaction.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
            transaction.hash = bundleHash.clone();
            transactions = ArrayUtils.addAll(transactions, new Transaction(transaction));
        }
        for(Transaction transaction: transactions) {
            transaction.store().get();
        }

        bundle = transactions[0].getBundleTransactions();
        TangleAccessor.instance().shutdown();
        assertEquals(bundle.length, transactions.length);
    }
    @Test
    public void fromHash() throws Exception {

    }

    @Test
    public void fromHash1() throws Exception {

    }

    @Test
    public void update() throws Exception {

    }

    @Test
    public void getBranchTransaction() throws Exception {

    }

    @Test
    public void getTrunkTransaction() throws Exception {

    }

    @Test
    public void trits() throws Exception {

    }

    @Test
    public void getBytes() throws Exception {

    }

    @Test
    public void getHash() throws Exception {

    }

    @Test
    public void getAddress() throws Exception {

    }

    @Test
    public void getTag() throws Exception {

    }

    @Test
    public void getBundleHash() throws Exception {

    }

    @Test
    public void getTrunkTransactionHash() throws Exception {

    }

    @Test
    public void getBranchTransactionHash() throws Exception {

    }

    @Test
    public void getValue() throws Exception {

    }

    @Test
    public void value() throws Exception {

    }

    @Test
    public void setValidity() throws Exception {

    }

    @Test
    public void getValidity() throws Exception {

    }

    @Test
    public void getCurrentIndex() throws Exception {

    }

    @Test
    public void getLastIndex() throws Exception {

    }

}