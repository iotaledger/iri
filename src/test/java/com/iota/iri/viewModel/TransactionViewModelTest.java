package com.iota.iri.viewModel;

import com.iota.iri.hash.Curl;
import com.iota.iri.model.Transaction;
import com.iota.iri.tangle.Tangle;
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
public class TransactionViewModelTest {
    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void getBundleTransactions() throws Exception {
        Tangle.instance().addPersistenceProvider(new RocksDBPersistenceProvider());
        Tangle.instance().init();
        Random r = new Random();
        byte[] bundleHash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
        TransactionViewModel[] bundle, transactionViewModels;
        transactionViewModels = Arrays.stream(new com.iota.iri.model.Transaction[4]).map(t -> {
            com.iota.iri.model.Transaction transaction = new com.iota.iri.model.Transaction();
            transaction.bundle.hash = bundleHash.clone();
            transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
            transaction.hash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
            return new TransactionViewModel(transaction);
        }).toArray(TransactionViewModel[]::new);
        {
            com.iota.iri.model.Transaction transaction = new com.iota.iri.model.Transaction();
            transaction.bundle.hash = bundleHash.clone();
            transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
            transaction.hash = bundleHash.clone();
            transactionViewModels = ArrayUtils.addAll(transactionViewModels, new TransactionViewModel(transaction));
        }
        for(TransactionViewModel transactionViewModel : transactionViewModels) {
            transactionViewModel.store().get();
        }

        bundle = transactionViewModels[0].getBundleTransactions();
        Tangle.instance().shutdown();
        assertEquals(bundle.length, transactionViewModels.length);
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
        Tangle.instance().addPersistenceProvider(new RocksDBPersistenceProvider());
        Tangle.instance().init();
        Random r = new Random();
        //byte[] bundleHash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
        TransactionViewModel transactionViewModel, branchTransaction;

        Transaction transaction = new Transaction();
        transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
        transaction.hash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
        branchTransaction = new TransactionViewModel(transaction);

        transaction = new Transaction();
        transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
        transaction.hash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
        transaction.branch.hash = branchTransaction.getHash();
        transactionViewModel = new TransactionViewModel(transaction);

        transactionViewModel.store().get();
        branchTransaction.store().get();

        TransactionViewModel branchTransactionQuery = transactionViewModel.getBranchTransaction();
        Tangle.instance().shutdown();
        assertArrayEquals(branchTransactionQuery.getHash(), branchTransaction.getHash());
    }

    @Test
    public void getTrunkTransaction() throws Exception {
        Tangle.instance().addPersistenceProvider(new RocksDBPersistenceProvider());
        Tangle.instance().init();
        Random r = new Random();
        //byte[] bundleHash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
        TransactionViewModel transactionViewModel, trunkTransactionViewModel;

        Transaction transaction = new Transaction();
        transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
        transaction.hash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
        trunkTransactionViewModel = new TransactionViewModel(transaction);

        transaction = new Transaction();
        transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
        transaction.hash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
        transaction.trunk.hash = trunkTransactionViewModel.getHash();
        transactionViewModel = new TransactionViewModel(transaction);

        transactionViewModel.store().get();
        trunkTransactionViewModel.store().get();

        TransactionViewModel trunkTransactionQuery = transactionViewModel.getTrunkTransaction();
        Tangle.instance().shutdown();
        assertArrayEquals(trunkTransactionQuery.getHash(), trunkTransactionViewModel.getHash());
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