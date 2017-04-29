package com.iota.iri.controllers;

import com.iota.iri.conf.Configuration;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.Approvee;
import com.iota.iri.model.Bundle;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProviderTest;
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

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();

    private static final Random seed = new Random();

    @BeforeClass
    public static void setUp() throws Exception {
        dbFolder.create();
        logFolder.create();
        Configuration.put(Configuration.DefaultConfSettings.DB_PATH, dbFolder.getRoot().getAbsolutePath());
        Configuration.put(Configuration.DefaultConfSettings.DB_LOG_PATH, logFolder.getRoot().getAbsolutePath());
        Tangle.instance().addPersistenceProvider(RocksDBPersistenceProviderTest.rocksDBPersistenceProvider);
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

        //bundleValidator = transactionViewModels[0].getBundleTransactions();
        //assertEquals(bundleValidator.length, transactionViewModels.length);
    }

    @Test
    public void getBranchTransaction() throws Exception {
        TransactionViewModel transactionViewModel, branchTransaction;
        int[] trits = getRandomTransactionTrits();
        branchTransaction = new TransactionViewModel(trits, Hash.calculate(trits));

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


        int[] trits = getRandomTransactionTrits();
        trunkTx = new TransactionViewModel(trits, Hash.calculate(trits));

        branchTx = new TransactionViewModel(trits, Hash.calculate(trits));

        int[] childTx = getRandomTransactionTrits();
        System.arraycopy(trunkTx.getHash().trits(), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
        System.arraycopy(branchTx.getHash().trits(), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
        transactionViewModel = new TransactionViewModel(childTx, Hash.calculate(childTx));

        childTx = getRandomTransactionTrits();
        System.arraycopy(trunkTx.getHash().trits(), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
        System.arraycopy(branchTx.getHash().trits(), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
        otherTxVM = new TransactionViewModel(childTx, Hash.calculate(childTx));

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

    @Test
    public void updateHeightShouldWork() throws Exception {
        TransactionViewModel transactionViewModel, transactionViewModel1, transactionViewModel2, transactionViewModel3;
        transactionViewModel = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(Hash.NULL_HASH,
                Hash.NULL_HASH), getRandomTransactionHash());
        transactionViewModel.store();
        transactionViewModel1 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transactionViewModel.getHash(),
                Hash.NULL_HASH), getRandomTransactionHash());
        transactionViewModel1.store();
        transactionViewModel2 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transactionViewModel1.getHash(),
                Hash.NULL_HASH), getRandomTransactionHash());
        transactionViewModel2.store();
        transactionViewModel3 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transactionViewModel2.getHash(),
                Hash.NULL_HASH), getRandomTransactionHash());
        transactionViewModel3.store();


        transactionViewModel3.updateHeights();

        transactionViewModel = TransactionViewModel.fromHash(transactionViewModel.getHash());
        transactionViewModel1 = TransactionViewModel.fromHash(transactionViewModel1.getHash());
        transactionViewModel2 = TransactionViewModel.fromHash(transactionViewModel2.getHash());
        transactionViewModel3 = TransactionViewModel.fromHash(transactionViewModel3.getHash());

        assertEquals(4, transactionViewModel3.getHeight());
        assertEquals(3, transactionViewModel2.getHeight());
        assertEquals(2, transactionViewModel1.getHeight());
        assertEquals(1, transactionViewModel.getHeight());
    }

    @Test
    public void updateHeightPrefilledSlotShouldFail() throws Exception {
        TransactionViewModel transactionViewModel, transactionViewModel1, transactionViewModel2, transactionViewModel3;
        transactionViewModel = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(getRandomTransactionHash(),
                Hash.NULL_HASH), getRandomTransactionHash());
        transactionViewModel.store();
        transactionViewModel1 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transactionViewModel.getHash(),
                Hash.NULL_HASH), getRandomTransactionHash());
        transactionViewModel1.store();
        transactionViewModel2 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transactionViewModel1.getHash(),
                Hash.NULL_HASH), getRandomTransactionHash());
        transactionViewModel2.store();
        transactionViewModel3 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transactionViewModel2.getHash(),
                Hash.NULL_HASH), getRandomTransactionHash());
        transactionViewModel3.store();


        transactionViewModel3.updateHeights();

        transactionViewModel = TransactionViewModel.fromHash(transactionViewModel.getHash());
        transactionViewModel1 = TransactionViewModel.fromHash(transactionViewModel1.getHash());
        transactionViewModel2 = TransactionViewModel.fromHash(transactionViewModel2.getHash());
        transactionViewModel3 = TransactionViewModel.fromHash(transactionViewModel3.getHash());

        assertEquals(0, transactionViewModel3.getHeight());
        assertEquals(0, transactionViewModel2.getHeight());
        assertEquals(0, transactionViewModel1.getHeight());
        assertEquals(0, transactionViewModel.getHeight());
    }

    @Test
    public void findShouldBeSuccessful() throws Exception {
        int[] trits = getRandomTransactionTrits();
        TransactionViewModel transactionViewModel = new TransactionViewModel(trits, Hash.calculate(trits));
        transactionViewModel.store();
        Hash hash = transactionViewModel.getHash();
        Assert.assertArrayEquals(TransactionViewModel.find(Arrays.copyOf(hash.bytes(), TransactionRequester.REQUEST_HASH_SIZE)).getBytes(), transactionViewModel.getBytes());
    }

    @Test
    public void findShoultReturnNull() throws Exception {
        int[] trits = getRandomTransactionTrits();
        TransactionViewModel transactionViewModel = new TransactionViewModel(trits, Hash.calculate(trits));
        trits = getRandomTransactionTrits();
        TransactionViewModel transactionViewModelNoSave = new TransactionViewModel(trits, Hash.calculate(trits));
        transactionViewModel.store();
        Hash hash = transactionViewModelNoSave.getHash();
        Assert.assertFalse(Arrays.equals(TransactionViewModel.find(Arrays.copyOf(hash.bytes(), TransactionRequester.REQUEST_HASH_SIZE)).getBytes(), transactionViewModel.getBytes()));
    }

    private Transaction getRandomTransaction(Random seed) {
        Transaction transaction = new Transaction();
        transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE]).map(i -> seed.nextInt(3)-1).toArray());
        transaction.hash = new Hash(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> seed.nextInt(3)-1).toArray());
        return transaction;
    }
    public static int[] getRandomTransactionWithTrunkAndBranch(Hash trunk, Hash branch) {
        int[] trits = getRandomTransactionTrits();
        System.arraycopy(trunk.trits(), 0, trits, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET,
                TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
        System.arraycopy(branch.trits(), 0, trits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET,
                TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
        return trits;
    }
    public static int[] getRandomTransactionTrits() {
        return Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> seed.nextInt(3)-1).toArray();
    }
    public static Hash getRandomTransactionHash() {
        return new Hash(Arrays.stream(new int[Hash.SIZE_IN_TRITS]).map(i -> seed.nextInt(3)-1).toArray());
    }
}