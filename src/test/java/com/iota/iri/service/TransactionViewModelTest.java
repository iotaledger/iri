package com.iota.iri.service;

import com.iota.iri.hash.Curl;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.service.TransactionViewModel;
import com.iota.iri.tangle.Tangle;
import com.iota.iri.tangle.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Created by paul on 3/5/17 for iri.
 */
public class TransactionViewModelTest {
    @Rule
    public TemporaryFolder dbFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        Tangle.instance().addPersistenceProvider(new RocksDBPersistenceProvider());
         Tangle.instance().init(dbFolder.getRoot().getAbsolutePath());
    }

    @After
    public void tearDown() throws Exception {
        Tangle.instance().shutdown();
    }

    @Test
    public void getBundleTransactions() throws Exception {
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
        assertEquals(bundle.length, transactionViewModels.length);
    }

    @Test
    public void getBranchTransaction() throws Exception {
        Random seed = new Random();
        TransactionViewModel transactionViewModel, branchTransaction;

        branchTransaction = new TransactionViewModel(getRandomTransaction(seed));

        Transaction transaction = getRandomTransaction(seed);
        transaction.branch.hash = branchTransaction.getHash();
        transactionViewModel = new TransactionViewModel(transaction);

        transactionViewModel.store().get();
        branchTransaction.store().get();

        TransactionViewModel branchTransactionQuery = transactionViewModel.getBranchTransaction();
        assertArrayEquals(branchTransactionQuery.getHash(), branchTransaction.getHash());
    }

    @Test
    public void getTrunkTransaction() throws Exception {
        Random seed = new Random();
        TransactionViewModel transactionViewModel, trunkTransactionViewModel;

        trunkTransactionViewModel = new TransactionViewModel(getRandomTransaction(seed));

        Transaction transaction = getRandomTransaction(seed);
        transaction.trunk.hash = trunkTransactionViewModel.getHash();
        transactionViewModel = new TransactionViewModel(transaction);

        transactionViewModel.store().get();
        trunkTransactionViewModel.store().get();

        TransactionViewModel trunkTransactionQuery = transactionViewModel.getTrunkTransaction();
        assertArrayEquals(trunkTransactionQuery.getHash(), trunkTransactionViewModel.getHash());
    }

    @Test
    public void getApprovers() throws Exception {
        Random seed = new Random();
        TransactionViewModel transactionViewModel, trunkTxApprover, branchTxApprover;

        Transaction transaction;
        transactionViewModel = new TransactionViewModel(getRandomTransaction(seed));

        transaction = getRandomTransaction(seed);
        transaction.trunk.hash = transactionViewModel.getHash();
        trunkTxApprover = new TransactionViewModel(transaction);

        transaction = getRandomTransaction(seed);
        transaction.trunk.hash = transactionViewModel.getHash();
        branchTxApprover = new TransactionViewModel(transaction);

        transactionViewModel.store().get();
        trunkTxApprover.store().get();
        branchTxApprover.store().get();

        Hash[] approvers = transactionViewModel.getApprovers();
        assertNotEquals(Arrays.stream(approvers).filter(hash -> Arrays.equals(hash.bytes(), trunkTxApprover.getHash())).toArray().length, 0);
        assertNotEquals(Arrays.stream(approvers).filter(hash -> Arrays.equals(hash.bytes(), branchTxApprover.getHash())).toArray().length, 0);
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

    private Transaction getRandomTransaction(Random seed) {
        Transaction transaction = new Transaction();
        transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> seed.nextInt(3)-1).toArray());
        transaction.hash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> seed.nextInt(3)-1).toArray());
        return transaction;
    }
}