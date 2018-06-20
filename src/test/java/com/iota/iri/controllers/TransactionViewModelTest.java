package com.iota.iri.controllers;

import com.iota.iri.conf.Configuration;
import com.iota.iri.hash.Sponge;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Converter;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by paul on 3/5/17 for iri.
 */
public class TransactionViewModelTest {

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    Logger log = LoggerFactory.getLogger(TransactionViewModelTest.class);
    private static Tangle tangle = new Tangle();

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
    }

    @AfterClass
    public static void tearDown() throws Exception {
        tangle.shutdown();
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


        int[] trits = getRandomTransactionTrits();
        trunkTx = new TransactionViewModel(trits, Hash.calculate(SpongeFactory.Mode.CURLP81, trits));

        branchTx = new TransactionViewModel(trits, Hash.calculate(SpongeFactory.Mode.CURLP81, trits));

        int[] childTx = getRandomTransactionTrits();
        System.arraycopy(trunkTx.getHash().trits(), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
        System.arraycopy(branchTx.getHash().trits(), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
        transactionViewModel = new TransactionViewModel(childTx, Hash.calculate(SpongeFactory.Mode.CURLP81, childTx));

        childTx = getRandomTransactionTrits();
        System.arraycopy(trunkTx.getHash().trits(), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
        System.arraycopy(branchTx.getHash().trits(), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
        otherTxVM = new TransactionViewModel(childTx, Hash.calculate(SpongeFactory.Mode.CURLP81, childTx));

        otherTxVM.store(tangle);
        transactionViewModel.store(tangle);
        trunkTx.store(tangle);
        branchTx.store(tangle);

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
        int count = 4;
        TransactionViewModel[] transactionViewModels = new TransactionViewModel[count];
        Hash hash = getRandomTransactionHash();
        transactionViewModels[0] = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(Hash.NULL_HASH,
                Hash.NULL_HASH), hash);
        transactionViewModels[0].store(tangle);
        for(int i = 0; ++i < count; ) {
            transactionViewModels[i] = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(hash,
                    Hash.NULL_HASH), hash = getRandomTransactionHash());
            transactionViewModels[i].store(tangle);
        }

        transactionViewModels[count-1].updateHeights(tangle);

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
            transactionViewModels[i].store(tangle);
        }

        transactionViewModels[count-1].updateHeights(tangle);

        for(int i = count; i > 1; ) {
            assertEquals(0, TransactionViewModel.fromHash(tangle, transactionViewModels[--i].getHash()).getHeight());
        }
    }

    @Test
    public void findShouldBeSuccessful() throws Exception {
        int[] trits = getRandomTransactionTrits();
        TransactionViewModel transactionViewModel = new TransactionViewModel(trits, Hash.calculate(SpongeFactory.Mode.CURLP81, trits));
        transactionViewModel.store(tangle);
        Hash hash = transactionViewModel.getHash();
        Assert.assertArrayEquals(TransactionViewModel.find(tangle, Arrays.copyOf(hash.bytes(), Integer.parseInt(Configuration.REQ_HASH_SIZE))).getBytes(), transactionViewModel.getBytes());
    }

    @Test
    public void findShouldReturnNull() throws Exception {
        int[] trits = getRandomTransactionTrits();
        TransactionViewModel transactionViewModel = new TransactionViewModel(trits, Hash.calculate(SpongeFactory.Mode.CURLP81, trits));
        trits = getRandomTransactionTrits();
        TransactionViewModel transactionViewModelNoSave = new TransactionViewModel(trits, Hash.calculate(SpongeFactory.Mode.CURLP81, trits));
        transactionViewModel.store(tangle);
        Hash hash = transactionViewModelNoSave.getHash();
        Assert.assertFalse(Arrays.equals(TransactionViewModel.find(tangle, Arrays.copyOf(hash.bytes(), Integer.parseInt(Configuration.REQ_HASH_SIZE))).getBytes(), transactionViewModel.getBytes()));
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
        new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(Hash.NULL_HASH, Hash.NULL_HASH), hash).store(tangle);
        TransactionViewModel transactionViewModel;
        boolean pop = false;
        for (i = 0; i++ < max;) {
            hash = getRandomTransactionHash();
            j = hashes.size();
            transactionViewModel = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(hashes.get(seed.nextInt(j)), hashes.get(seed.nextInt(j))), hash);
            start = System.nanoTime();
            transactionViewModel.store(tangle);
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

        int[] trits = Arrays.stream(new int[TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE]).map(i -> seed.nextInt(3)-1).toArray();
        transaction.bytes = Converter.allocateBytesForTrits(trits.length);
        Converter.bytes(trits, 0, transaction.bytes, 0, trits.length);
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
    public static int[] getRandomTransactionWithTrunkAndBranchValidBundle(Hash trunk, Hash branch) {
        int[] trits = getRandomTransactionTrits();
        System.arraycopy(trunk.trits(), 0, trits, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET,
                TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
        System.arraycopy(branch.trits(), 0, trits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET,
                TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
        System.arraycopy(Hash.NULL_HASH.trits(), 0, trits, TransactionViewModel.CURRENT_INDEX_TRINARY_OFFSET,
                TransactionViewModel.CURRENT_INDEX_TRINARY_SIZE);
        System.arraycopy(Hash.NULL_HASH.trits(), 0, trits, TransactionViewModel.LAST_INDEX_TRINARY_OFFSET,
                TransactionViewModel.LAST_INDEX_TRINARY_SIZE);
        System.arraycopy(Hash.NULL_HASH.trits(), 0, trits, TransactionViewModel.VALUE_TRINARY_OFFSET,
                TransactionViewModel.VALUE_TRINARY_SIZE);

        final Sponge curlInstance = SpongeFactory.create(SpongeFactory.Mode.KERL);
        final int[] bundleHashTrits = new int[TransactionViewModel.BUNDLE_TRINARY_SIZE];
        curlInstance.reset();
        curlInstance.absorb(trits, TransactionViewModel.ESSENCE_TRINARY_OFFSET, TransactionViewModel.ESSENCE_TRINARY_SIZE);
        curlInstance.squeeze(bundleHashTrits, 0, bundleHashTrits.length);

        System.arraycopy(bundleHashTrits, 0, trits, TransactionViewModel.BUNDLE_TRINARY_OFFSET,
                TransactionViewModel.BUNDLE_TRINARY_SIZE);

        return trits;
    }
    public static int[] getRandomTransactionTrits() {
        return Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> seed.nextInt(3)-1).toArray();
    }
    public static Hash getRandomTransactionHash() {
        return new Hash(Arrays.stream(new int[Hash.SIZE_IN_TRITS]).map(i -> seed.nextInt(3)-1).toArray());
    }
}