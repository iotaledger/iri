package com.iota.iri.controllers;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.cache.Cache;
import com.iota.iri.cache.CacheManager;
import com.iota.iri.cache.impl.CacheConfigurationImpl;
import com.iota.iri.cache.impl.CacheManagerImpl;
import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.impl.SnapshotMockUtils;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Converter;

import java.util.*;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.iota.iri.TransactionTestUtils.getTransactionHash;
import static com.iota.iri.TransactionTestUtils.getTransactionTrits;
import static com.iota.iri.TransactionTestUtils.getTransactionTritsWithTrunkAndBranch;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TransactionViewModelTest {

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    Logger log = LoggerFactory.getLogger(TransactionViewModelTest.class);
    private static Tangle tangle = new Tangle();
    private static Snapshot snapshot;
    private static final Random seed = new Random();


    @Before
    public void setUp() throws Exception {
        dbFolder.create();
        logFolder.create();
        RocksDBPersistenceProvider rocksDBPersistenceProvider;
        rocksDBPersistenceProvider =  new RocksDBPersistenceProvider(
                dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),1000,
                Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY);
        tangle.addPersistenceProvider(rocksDBPersistenceProvider);
        tangle.init();
        snapshot = SnapshotMockUtils.createSnapshot();
    }

    @After
    public void tearDown() throws Exception {
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


        byte[] trits = getTransactionTrits();
        trunkTx = new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));

        branchTx = new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));

        byte[] childTx = getTransactionTrits();
        System.arraycopy(trunkTx.getHash().trits(), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
        System.arraycopy(branchTx.getHash().trits(), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
        transactionViewModel = new TransactionViewModel(childTx, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, childTx));

        childTx = getTransactionTrits();
        System.arraycopy(trunkTx.getHash().trits(), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
        System.arraycopy(branchTx.getHash().trits(), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
        otherTxVM = new TransactionViewModel(childTx, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, childTx));

        otherTxVM.store(tangle, snapshot);
        transactionViewModel.store(tangle, snapshot);
        trunkTx.store(tangle, snapshot);
        branchTx.store(tangle, snapshot);

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
            byte[] trits = getTransactionTrits(), searchTrits;
            System.arraycopy(new byte[TransactionViewModel.VALUE_TRINARY_SIZE], 0, trits, TransactionViewModel.VALUE_TRINARY_OFFSET, TransactionViewModel.VALUE_TRINARY_SIZE);
            Converter.copyTrits(seed.nextLong(), trits, TransactionViewModel.VALUE_TRINARY_OFFSET, TransactionViewModel.VALUE_USABLE_TRINARY_SIZE);
            System.arraycopy(blanks, 0, trits, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET-blanks.length, blanks.length);
            System.arraycopy(blanks, 0, trits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET-blanks.length, blanks.length);
            System.arraycopy(blanks, 0, trits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET + TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE-blanks.length, blanks.length);
            Hash hash = getTransactionHash();
            TransactionViewModel transactionViewModel = new TransactionViewModel(trits, hash);
            transactionViewModel.store(tangle, snapshot);
            assertArrayEquals(transactionViewModel.trits(), TransactionViewModel.fromHash(tangle, transactionViewModel.getHash()).trits());
        }
    }

    @Test
    public void getBytes() throws Exception {
        for(int i=0; i++ < 1000;) {
            byte[] trits = getTransactionTrits();
            System.arraycopy(new byte[TransactionViewModel.VALUE_TRINARY_SIZE], 0, trits, TransactionViewModel.VALUE_TRINARY_OFFSET, TransactionViewModel.VALUE_TRINARY_SIZE);
            Converter.copyTrits(seed.nextLong(), trits, TransactionViewModel.VALUE_TRINARY_OFFSET, TransactionViewModel.VALUE_USABLE_TRINARY_SIZE);
            Hash hash = getTransactionHash();
            TransactionViewModel transactionViewModel = new TransactionViewModel(trits, hash);
            transactionViewModel.store(tangle, snapshot);
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
        Hash hash = getTransactionHash();
        transactionViewModels[0] = new TransactionViewModel(getTransactionTritsWithTrunkAndBranch(Hash.NULL_HASH,
                Hash.NULL_HASH), hash);
        transactionViewModels[0].store(tangle, snapshot);
        for(int i = 0; ++i < count; ) {
            transactionViewModels[i] = new TransactionViewModel(getTransactionTritsWithTrunkAndBranch(hash,
                    Hash.NULL_HASH), hash = getTransactionHash());
            transactionViewModels[i].store(tangle, snapshot);
        }

        transactionViewModels[count-1].updateHeights(tangle, snapshot);

        for(int i = count; i > 1; ) {
            assertEquals(i, TransactionViewModel.fromHash(tangle, transactionViewModels[--i].getHash()).getHeight());
        }
    }

    @Test
    public void updateHeightPrefilledSlotShouldFail() throws Exception {
        int count = 4;
        TransactionViewModel[] transactionViewModels = new TransactionViewModel[count];
        Hash hash = getTransactionHash();
        for(int i = 0; ++i < count; ) {
            transactionViewModels[i] = new TransactionViewModel(getTransactionTritsWithTrunkAndBranch(hash,
                    Hash.NULL_HASH), hash = getTransactionHash());
            transactionViewModels[i].store(tangle, snapshot);
        }

        transactionViewModels[count-1].updateHeights(tangle, snapshot);

        for(int i = count; i > 1; ) {
            assertEquals(0, TransactionViewModel.fromHash(tangle, transactionViewModels[--i].getHash()).getHeight());
        }
    }

    @Test
    public void findShouldBeSuccessful() throws Exception {
        byte[] trits = getTransactionTrits();
        TransactionViewModel transactionViewModel = new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));
        transactionViewModel.store(tangle, snapshot);
        Hash hash = transactionViewModel.getHash();
        Assert.assertArrayEquals(TransactionViewModel.find(tangle,
                Arrays.copyOf(hash.bytes(), MainnetConfig.Defaults.REQUEST_HASH_SIZE)).getBytes(),
                transactionViewModel.getBytes());
    }

    @Test
    public void findShouldReturnNull() throws Exception {
        byte[] trits = getTransactionTrits();
        TransactionViewModel transactionViewModel = new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));
        trits = getTransactionTrits();
        TransactionViewModel transactionViewModelNoSave = new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));
        transactionViewModel.store(tangle, snapshot);
        Hash hash = transactionViewModelNoSave.getHash();
        Assert.assertFalse(Arrays.equals(TransactionViewModel.find(tangle,
                Arrays.copyOf(hash.bytes(), new MainnetConfig().getRequestHashSize())).getBytes(), transactionViewModel.getBytes()));
    }

    //@Test
    public void testManyTXInDB() throws Exception {
        int i, j;
        LinkedList<Hash> hashes = new LinkedList<>();
        Hash hash;
        hash = getTransactionHash();
        hashes.add(hash);
        long start, diff, diffget;
        long subSumDiff=0,maxdiff=0, sumdiff = 0;
        int max = 990 * 1000;
        int interval1 = 50;
        int interval = interval1*10;
        log.info("Starting Test. #TX: {}", TransactionViewModel.getNumberOfStoredTransactions(tangle));
        new TransactionViewModel(getTransactionTritsWithTrunkAndBranch(Hash.NULL_HASH, Hash.NULL_HASH), hash).store(tangle, snapshot);
        TransactionViewModel transactionViewModel;
        boolean pop = false;
        for (i = 0; i++ < max;) {
            hash = getTransactionHash();
            j = hashes.size();
            transactionViewModel = new TransactionViewModel(getTransactionTritsWithTrunkAndBranch(hashes.get(seed.nextInt(j)), hashes.get(seed.nextInt(j))), hash);
            start = System.nanoTime();
            transactionViewModel.store(tangle, snapshot);
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

    @Test
    public void firstShouldFindTx() throws Exception {
        byte[] trits = getTransactionTrits();
        TransactionViewModel transactionViewModel = new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));
        transactionViewModel.store(tangle, snapshot);

        TransactionViewModel result = TransactionViewModel.first(tangle);
        Assert.assertEquals(transactionViewModel.getHash(), result.getHash());
    }

    @Test
    public void updateTxShouldBeSavedBeforeRelease() throws Exception {
        CacheManager cacheManager = new CacheManagerImpl(new MainnetConfig());
        cacheManager.clearAllCaches();
        cacheManager.add(TransactionViewModel.class, new CacheConfigurationImpl(1, 1));
        tangle.setCacheManager(cacheManager);

        int numberOfTxs = 2;
        String trytes = "";

        TransactionViewModel tvms[] = new TransactionViewModel[numberOfTxs];
        for (int i = 0; i < numberOfTxs; i++) {
            trytes = TransactionTestUtils.nextWord(trytes);
            tvms[i] = TransactionTestUtils.createTransactionWithTrytes(trytes);
            tvms[i].store(tangle, snapshot);
        }
        tvms[0].isMilestone(tangle, snapshot, true);
        tvms[1].isMilestone(tangle, snapshot, true);

        Hash hash = tvms[0].getHash();
        TransactionViewModel tvm = new TransactionViewModel((Transaction) tangle.load(Transaction.class, hash), hash);
        Assert.assertTrue("TVM should be a milestone", tvm.isMilestone());
    }

    @Test
    public void updatedTxShouldBeSavedToDbBeforeTangleShutdown() throws Exception {
        Cache<Indexable, TransactionViewModel> cache = tangle.getCache(TransactionViewModel.class);
        int numberOfTxs = (int) cache.getConfiguration().getMaxSize() * 2;
        String trytes = "";

        TransactionViewModel tvms[] = new TransactionViewModel[numberOfTxs];
        for (int i = 0; i < numberOfTxs; i++) {
            trytes = TransactionTestUtils.nextWord(trytes);
            tvms[i] = TransactionTestUtils.createTransactionWithTrytes(trytes);
            tvms[i].store(tangle, snapshot);
        }

        List<Integer> tvmIndicesToUpdate = new ArrayList<>();
        for (int i = 0; i < numberOfTxs; i++) {
            tvmIndicesToUpdate.add(i);
        }
        Collections.shuffle(tvmIndicesToUpdate);
        tvmIndicesToUpdate = tvmIndicesToUpdate.subList(0, numberOfTxs / 2);

        for (int i : tvmIndicesToUpdate) {
            TransactionViewModel tvm = cache.get(tvms[i].getHash());
            if (tvm != null) {
                tvm.isMilestone(tangle, snapshot, true);
            }
        }

        // tangle shutdown
        TransactionViewModel.cacheReleaseAll(tangle);

        for (int i : tvmIndicesToUpdate) {
            Hash hash = tvms[i].getHash();
            TransactionViewModel tvm = new TransactionViewModel((Transaction) tangle.load(Transaction.class, hash),
                    hash);
            Assert.assertTrue("TVM should be a milestone", tvm.isMilestone());
        }
    }

    @Test
    public void deletedTxFromDbShouldDeleteFromCache() throws Exception {
        Cache<Indexable, TransactionViewModel> cache = tangle.getCache(TransactionViewModel.class);

        Transaction tx = new Transaction();
        Hash hash = HashFactory.TRANSACTION
                .create("TEST9TRANSACTION9TEST9TRANSACTION9TEST9TRANSACTION9TEST9TRANSACTION9TEST999999999");
        tx.address = hash;
        TransactionViewModel tvm = new TransactionViewModel(tx, hash);

        tvm.store(tangle, snapshot);
        tvm.delete(tangle);
        Assert.assertNull("TVM should be null", cache.get(hash));
    }

    @Test
    public void deletedTxShouldNotBeBroughtBackToCache() throws Exception {
        Transaction tx = new Transaction();
        Hash hash = HashFactory.TRANSACTION
                .create("TEST9TRANSACTION9TEST9TRANSACTION9TEST9TRANSACTION9TEST9TRANSACTION9TEST999999999");
        tx.address = hash;
        TransactionViewModel tvm = new TransactionViewModel(tx, hash);

        tvm.store(tangle, snapshot);
        tvm.delete(tangle);

        TransactionViewModel.fromHash(tangle, hash);

        Cache<Indexable, TransactionViewModel> cache = tangle.getCache(TransactionViewModel.class);
        Assert.assertNull("TVM should be null", cache.get(hash));
    }
}
