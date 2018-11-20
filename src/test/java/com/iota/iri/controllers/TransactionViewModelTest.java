package com.iota.iri.controllers;

import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.impl.SnapshotProviderImpl;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Converter;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

public class TransactionViewModelTest {

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    Logger log = LoggerFactory.getLogger(TransactionViewModelTest.class);
    private static Tangle tangle = new Tangle();
    private static SnapshotProvider snapshotProvider;

    private static final Random seed = new Random();

    @BeforeClass
    public static void setUp() throws Exception {
        dbFolder.create();
        logFolder.create();
        RocksDBPersistenceProvider rocksDBPersistenceProvider;
        rocksDBPersistenceProvider = new RocksDBPersistenceProvider(dbFolder.getRoot().getAbsolutePath(),
                logFolder.getRoot().getAbsolutePath(),1000);
        tangle.addPersistenceProvider(rocksDBPersistenceProvider);
        tangle.init();
        snapshotProvider = new SnapshotProviderImpl().init(new MainnetConfig());
    }

    @AfterClass
    public static void tearDown() throws Exception {
        tangle.shutdown();
        snapshotProvider.shutdown();
        dbFolder.delete();
        logFolder.delete();
    }

    @Test
    public void getBundleTransactions() throws Exception {
    }

    @Test
    public void getBranchTransaction() throws Exception {
    }

    @Test
    public void getTrunkTransaction() throws Exception {
    }

    @Test
    public void getApprovers() throws Exception {
        TransactionViewModel transactionViewModel, otherTxVM, trunkTx, branchTx;


        byte[] trits = getRandomTransactionTrits();
        trunkTx = new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));

        branchTx = new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));

        byte[] childTx = getRandomTransactionTrits();
        System.arraycopy(trunkTx.getHash().trits(), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
        System.arraycopy(branchTx.getHash().trits(), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
        transactionViewModel = new TransactionViewModel(childTx, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, childTx));

        childTx = getRandomTransactionTrits();
        System.arraycopy(trunkTx.getHash().trits(), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
        System.arraycopy(branchTx.getHash().trits(), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
        otherTxVM = new TransactionViewModel(childTx, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, childTx));

        otherTxVM.store(tangle, snapshotProvider.getInitialSnapshot());
        transactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot());
        trunkTx.store(tangle, snapshotProvider.getInitialSnapshot());
        branchTx.store(tangle, snapshotProvider.getInitialSnapshot());

        Set<Hash> approvers = trunkTx.getApprovers(tangle).getHashes();
        assertNotEquals(approvers.size(), 0);
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
        byte[] blanks = new byte[13];
        for(int i=0; i++ < 1000;) {
            byte[] trits = getRandomTransactionTrits(), searchTrits;
            System.arraycopy(new byte[TransactionViewModel.VALUE_TRINARY_SIZE], 0, trits, TransactionViewModel.VALUE_TRINARY_OFFSET, TransactionViewModel.VALUE_TRINARY_SIZE);
            Converter.copyTrits(seed.nextLong(), trits, TransactionViewModel.VALUE_TRINARY_OFFSET, TransactionViewModel.VALUE_USABLE_TRINARY_SIZE);
            System.arraycopy(blanks, 0, trits, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET-blanks.length, blanks.length);
            System.arraycopy(blanks, 0, trits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET-blanks.length, blanks.length);
            System.arraycopy(blanks, 0, trits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET + TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE-blanks.length, blanks.length);
            Hash hash = getRandomTransactionHash();
            TransactionViewModel transactionViewModel = new TransactionViewModel(trits, hash);
            transactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot());
            assertArrayEquals(transactionViewModel.trits(), TransactionViewModel.fromHash(tangle, transactionViewModel.getHash()).trits());
        }
    }

    @Test
    public void getBytes() throws Exception {
        for(int i=0; i++ < 1000;) {
            byte[] trits = getRandomTransactionTrits();
            System.arraycopy(new byte[TransactionViewModel.VALUE_TRINARY_SIZE], 0, trits, TransactionViewModel.VALUE_TRINARY_OFFSET, TransactionViewModel.VALUE_TRINARY_SIZE);
            Converter.copyTrits(seed.nextLong(), trits, TransactionViewModel.VALUE_TRINARY_OFFSET, TransactionViewModel.VALUE_USABLE_TRINARY_SIZE);
            Hash hash = getRandomTransactionHash();
            TransactionViewModel transactionViewModel = new TransactionViewModel(trits, hash);
            transactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot());
            assertArrayEquals(transactionViewModel.getBytes(), TransactionViewModel.fromHash(tangle, transactionViewModel.getHash()).getBytes());
        }
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
        int count = 4;
        TransactionViewModel[] transactionViewModels = new TransactionViewModel[count];
        Hash hash = getRandomTransactionHash();
        transactionViewModels[0] = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(Hash.NULL_HASH,
                Hash.NULL_HASH), hash);
        transactionViewModels[0].store(tangle, snapshotProvider.getInitialSnapshot());
        for(int i = 0; ++i < count; ) {
            transactionViewModels[i] = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(hash,
                    Hash.NULL_HASH), hash = getRandomTransactionHash());
            transactionViewModels[i].store(tangle, snapshotProvider.getInitialSnapshot());
        }

        transactionViewModels[count-1].updateHeights(tangle, snapshotProvider.getInitialSnapshot());

        for(int i = count; i > 1; ) {
            assertEquals(i, TransactionViewModel.fromHash(tangle, transactionViewModels[--i].getHash()).getHeight());
        }
    }

    @Test
    public void updateHeightPrefilledSlotShouldFail() throws Exception {
        int count = 4;
        TransactionViewModel[] transactionViewModels = new TransactionViewModel[count];
        Hash hash = getRandomTransactionHash();
        for(int i = 0; ++i < count; ) {
            transactionViewModels[i] = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(hash,
                    Hash.NULL_HASH), hash = getRandomTransactionHash());
            transactionViewModels[i].store(tangle, snapshotProvider.getInitialSnapshot());
        }

        transactionViewModels[count-1].updateHeights(tangle, snapshotProvider.getInitialSnapshot());

        for(int i = count; i > 1; ) {
            assertEquals(0, TransactionViewModel.fromHash(tangle, transactionViewModels[--i].getHash()).getHeight());
        }
    }

    @Test
    public void findShouldBeSuccessful() throws Exception {
        byte[] trits = getRandomTransactionTrits();
        TransactionViewModel transactionViewModel = new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));
        transactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot());
        Hash hash = transactionViewModel.getHash();
        Assert.assertArrayEquals(TransactionViewModel.find(tangle,
                Arrays.copyOf(hash.bytes(), MainnetConfig.Defaults.REQ_HASH_SIZE)).getBytes(),
                transactionViewModel.getBytes());
    }

    @Test
    public void findShouldReturnNull() throws Exception {
        byte[] trits = getRandomTransactionTrits();
        TransactionViewModel transactionViewModel = new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));
        trits = getRandomTransactionTrits();
        TransactionViewModel transactionViewModelNoSave = new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));
        transactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot());
        Hash hash = transactionViewModelNoSave.getHash();
        Assert.assertFalse(Arrays.equals(TransactionViewModel.find(tangle,
                Arrays.copyOf(hash.bytes(), new MainnetConfig().getRequestHashSize())).getBytes(), transactionViewModel.getBytes()));
    }

    //@Test
    public void testManyTXInDB() throws Exception {
        int i, j;
        LinkedList<Hash> hashes = new LinkedList<>();
        Hash hash;
        hash = getRandomTransactionHash();
        hashes.add(hash);
        long start, diff, diffget;
        long subSumDiff=0,maxdiff=0, sumdiff = 0;
        int max = 990 * 1000;
        int interval1 = 50;
        int interval = interval1*10;
        log.info("Starting Test. #TX: {}", TransactionViewModel.getNumberOfStoredTransactions(tangle));
        new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(Hash.NULL_HASH, Hash.NULL_HASH), hash).store(tangle, snapshotProvider.getInitialSnapshot());
        TransactionViewModel transactionViewModel;
        boolean pop = false;
        for (i = 0; i++ < max;) {
            hash = getRandomTransactionHash();
            j = hashes.size();
            transactionViewModel = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(hashes.get(seed.nextInt(j)), hashes.get(seed.nextInt(j))), hash);
            start = System.nanoTime();
            transactionViewModel.store(tangle, snapshotProvider.getInitialSnapshot());
            diff = System.nanoTime() - start;
            subSumDiff += diff;
            if (diff>maxdiff) {
                maxdiff = diff;
            }
            hash = hashes.get(seed.nextInt(j));
            start = System.nanoTime();
            TransactionViewModel.fromHash(tangle, hash);
            diffget = System.nanoTime() - start;
            hashes.add(hash);
            if(pop || i > 1000) {
                hashes.removeFirst();
            }

            //log.info("{}", new String(new char[(int) ((diff/ 10000))]).replace('\0', '|'));
            if(i % interval1 == 0) {
                //log.info("{}", new String(new char[(int) (diff / 50000)]).replace('\0', '-'));
                //log.info("{}", new String(new char[(int) ((subSumDiff / interval1 / 100000))]).replace('\0', '|'));
                sumdiff += subSumDiff;
                subSumDiff = 0;
            }
            if(i % interval == 0) {
                log.info("Save time for {}: {} us.\tGet Time: {} us.\tMax time: {} us. Average: {}", i,
                        (diff / 1000) , diffget/1000, (maxdiff/ 1000), sumdiff/interval/1000);
                sumdiff = 0;
                maxdiff = 0;
            }
        }
        log.info("Done. #TX: {}", TransactionViewModel.getNumberOfStoredTransactions(tangle));
    }

    private Transaction getRandomTransaction(Random seed) {
        Transaction transaction = new Transaction();

        byte[] trits = new byte[TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE];
        for(int i = 0; i < trits.length; i++) {
            trits[i] = (byte) (seed.nextInt(3) - 1);
        }

        transaction.bytes = Converter.allocateBytesForTrits(trits.length);
        Converter.bytes(trits, 0, transaction.bytes, 0, trits.length);
        return transaction;
    }
    public static byte[] getRandomTransactionWithTrunkAndBranch(Hash trunk, Hash branch) {
        byte[] trits = getRandomTransactionTrits();
        System.arraycopy(trunk.trits(), 0, trits, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET,
                TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
        System.arraycopy(branch.trits(), 0, trits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET,
                TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
        return trits;
    }
    public static byte[] getRandomTransactionTrits() {
        byte[] out = new byte[TransactionViewModel.TRINARY_SIZE];

        for(int i = 0; i < out.length; i++) {
            out[i] = (byte) (seed.nextInt(3) - 1);
        }

        return out;
    }

    public static Hash getRandomTransactionHash() {
        byte[] out = new byte[Hash.SIZE_IN_TRITS];

        for(int i = 0; i < out.length; i++) {
            out[i] = (byte) (seed.nextInt(3) - 1);
        }

        return HashFactory.TRANSACTION.create(out);
    }
}
