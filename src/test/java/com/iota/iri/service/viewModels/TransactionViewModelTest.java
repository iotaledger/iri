package com.iota.iri.service.viewModels;

import com.iota.iri.conf.Configuration;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.Approvee;
import com.iota.iri.model.Bundle;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.service.tangle.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Converter;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Created by paul on 3/5/17 for iri.
 */
public class TransactionViewModelTest {

    static TemporaryFolder dbFolder = new TemporaryFolder();
    static TemporaryFolder logFolder = new TemporaryFolder();

    public static Random seed = new Random();

    @BeforeClass
    public static void setUp() throws Exception {
        dbFolder.create();
        logFolder.create();
        Configuration.put(Configuration.DefaultConfSettings.DB_PATH, dbFolder.getRoot().getAbsolutePath());
        Configuration.put(Configuration.DefaultConfSettings.DB_LOG_PATH, logFolder.getRoot().getAbsolutePath());
        Tangle.instance().addPersistenceProvider(new RocksDBPersistenceProvider());
        Tangle.instance().init();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        Tangle.instance().shutdown();
        dbFolder.delete();
    }

    @Test
    public void getBundleTransactions() throws Exception {
        Random r = new Random();
        Hash bundleHash = new Hash(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
        TransactionViewModel[] bundle, transactionViewModels;
        transactionViewModels = Arrays.stream(new com.iota.iri.model.Transaction[4]).map(t -> {
            com.iota.iri.model.Transaction transaction = new com.iota.iri.model.Transaction();
            transaction.bundle = new Bundle();
            transaction.bundle.hash = bundleHash;
            transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
            transaction.hash = new Hash(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
            return new TransactionViewModel(transaction);
        }).toArray(TransactionViewModel[]::new);
        {
            com.iota.iri.model.Transaction transaction = new com.iota.iri.model.Transaction();
            transaction.bundle = new Bundle();
            transaction.bundle.hash = bundleHash;
            transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
            transaction.hash = bundleHash;
            transactionViewModels = ArrayUtils.addAll(transactionViewModels, new TransactionViewModel(transaction));
        }
        for(TransactionViewModel transactionViewModel : transactionViewModels) {
            transactionViewModel.store();
        }

        //bundle = transactionViewModels[0].getBundleTransactions();
        //assertEquals(bundle.length, transactionViewModels.length);
    }

    @Test
    public void getBranchTransaction() throws Exception {
        TransactionViewModel transactionViewModel, branchTransaction;

        branchTransaction = new TransactionViewModel(getRandomTransactionTrits(seed));

        Transaction transaction = getRandomTransaction(seed);
        transaction.branch = new Approvee();
        transaction.branch.hash = branchTransaction.getHash();
        transactionViewModel = new TransactionViewModel(transaction);

        transactionViewModel.store();
        branchTransaction.store();

        TransactionViewModel branchTransactionQuery = transactionViewModel.getBranchTransaction();
        //assertArrayEquals(branchTransactionQuery.getHash(), branchTransaction.getHash());
    }

    @Test
    public void getTrunkTransaction() throws Exception {
        TransactionViewModel transactionViewModel, trunkTransactionViewModel;

        trunkTransactionViewModel = new TransactionViewModel(getRandomTransaction(seed));

        Transaction transaction = getRandomTransaction(seed);
        transaction.trunk = new Approvee();
        transaction.trunk.hash = trunkTransactionViewModel.getHash();
        transactionViewModel = new TransactionViewModel(transaction);

        transactionViewModel.store();
        trunkTransactionViewModel.store();

        TransactionViewModel trunkTransactionQuery = transactionViewModel.getTrunkTransaction();
        assertTrue(trunkTransactionQuery.getHash().equals(trunkTransactionViewModel.getHash()));
    }

    @Test
    public void getApprovers() throws Exception {
        TransactionViewModel transactionViewModel, otherTxVM, trunkTx, branchTx;


        trunkTx = new TransactionViewModel(getRandomTransactionTrits(seed));

        branchTx = new TransactionViewModel(getRandomTransactionTrits(seed));

        int[] childTx = getRandomTransactionTrits(seed);
        Curl curl = new Curl();
        System.arraycopy(trunkTx.getHashTrits(curl), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
        System.arraycopy(branchTx.getHashTrits(curl), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
        transactionViewModel = new TransactionViewModel(childTx);

        childTx = getRandomTransactionTrits(seed);
        System.arraycopy(trunkTx.getHashTrits(curl), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
        System.arraycopy(branchTx.getHashTrits(curl), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
        otherTxVM = new TransactionViewModel(childTx);

        otherTxVM.store();
        transactionViewModel.store();
        trunkTx.store();
        branchTx.store();

        Hash[] approvers = trunkTx.getApprovers();
        assertNotEquals(approvers.length, 0);
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
        /*
        int[] blanks = new int[13];
        for(int i=0; i++ < 1000;) {
            int[] trits = getRandomTransactionTrits(seed), searchTrits;
            System.arraycopy(new int[TransactionViewModel.VALUE_TRINARY_SIZE], 0, trits, TransactionViewModel.VALUE_TRINARY_OFFSET, TransactionViewModel.VALUE_TRINARY_SIZE);
            Converter.copyTrits(seed.nextLong(), trits, TransactionViewModel.VALUE_TRINARY_OFFSET, TransactionViewModel.VALUE_USABLE_TRINARY_SIZE);
            System.arraycopy(blanks, 0, trits, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET-blanks.length, blanks.length);
            System.arraycopy(blanks, 0, trits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET-blanks.length, blanks.length);
            System.arraycopy(blanks, 0, trits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET + TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE-blanks.length, blanks.length);
            TransactionViewModel transactionViewModel = new TransactionViewModel(trits);
            transactionViewModel.store();
            assertArrayEquals(transactionViewModel.trits(), TransactionViewModel.fromHash(transactionViewModel.getHash()).trits());
        }
        */
    }

    @Test
    public void getBytes() throws Exception {
        /*
        for(int i=0; i++ < 1000;) {
            int[] trits = getRandomTransactionTrits(seed);
            System.arraycopy(new int[TransactionViewModel.VALUE_TRINARY_SIZE], 0, trits, TransactionViewModel.VALUE_TRINARY_OFFSET, TransactionViewModel.VALUE_TRINARY_SIZE);
            Converter.copyTrits(seed.nextLong(), trits, TransactionViewModel.VALUE_TRINARY_OFFSET, TransactionViewModel.VALUE_USABLE_TRINARY_SIZE);
            TransactionViewModel transactionViewModel = new TransactionViewModel(trits);
            transactionViewModel.store();
            assertArrayEquals(transactionViewModel.getBytes(), TransactionViewModel.fromHash(transactionViewModel.getHash()).getBytes());
        }
        */
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

    @Test
    public void mightExist() throws Exception {

    }

    @Test
    public void update1() throws Exception {

    }

    @Test
    public void setAnalyzed() throws Exception {

    }


    @Test
    public void dump() throws Exception {

    }

    @Test
    public void store() throws Exception {

    }

    @Test
    public void updateTips() throws Exception {

    }

    @Test
    public void updateReceivedTransactionCount() throws Exception {

    }

    @Test
    public void updateApprovers() throws Exception {

    }

    @Test
    public void hashesFromQuery() throws Exception {

    }

    @Test
    public void approversFromHash() throws Exception {

    }

    @Test
    public void fromTag() throws Exception {

    }

    @Test
    public void fromBundle() throws Exception {

    }

    @Test
    public void fromAddress() throws Exception {

    }

    @Test
    public void getTransactionAnalyzedFlag() throws Exception {

    }

    @Test
    public void getType() throws Exception {

    }

    @Test
    public void setArrivalTime() throws Exception {

    }

    @Test
    public void getArrivalTime() throws Exception {

    }

    private Transaction getRandomTransaction(Random seed) {
        Transaction transaction = new Transaction();
        transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE]).map(i -> seed.nextInt(3)-1).toArray());
        transaction.hash = new Hash(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> seed.nextInt(3)-1).toArray());
        return transaction;
    }
    private int[] getRandomTransactionTrits(Random seed) {
        return Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> seed.nextInt(3)-1).toArray();
    }
}