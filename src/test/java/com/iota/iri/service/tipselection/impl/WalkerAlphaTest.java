package com.iota.iri.service.tipselection.impl;

import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashId;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.impl.SnapshotProviderImpl;
import com.iota.iri.service.tipselection.RatingCalculator;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.collections.interfaces.UnIterableMap;
import com.iota.iri.zmq.MessageQ;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static com.iota.iri.controllers.TransactionViewModelTest.*;

public class WalkerAlphaTest {
    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static Tangle tangle;
    private static SnapshotProvider snapshotProvider;
    private static WalkerAlpha walker;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @AfterClass
    public static void tearDown() throws Exception {
        tangle.shutdown();
        snapshotProvider.shutdown();
        dbFolder.delete();
        logFolder.delete();
    }

    @BeforeClass
    public static void setUp() throws Exception {
        tangle = new Tangle();
        snapshotProvider = new SnapshotProviderImpl().init(new MainnetConfig());
        dbFolder.create();
        logFolder.create();
        tangle.addPersistenceProvider(new RocksDBPersistenceProvider(dbFolder.getRoot().getAbsolutePath(), logFolder
                .getRoot().getAbsolutePath(), 1000));
        tangle.init();

        MessageQ messageQ = Mockito.mock(MessageQ.class);
        walker = new WalkerAlpha((Optional::of), tangle, messageQ, new Random(1), new MainnetConfig());
    }


    @Test
    public void testWalkEndsOnlyInRating() throws Exception {
        //build a small tangle - 1,2,3,4 point to  transaction
        TransactionViewModel transaction, transaction1, transaction2, transaction3, transaction4;
        transaction = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        transaction1 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction2 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction3 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());

        transaction.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction1.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction2.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction3.store(tangle, snapshotProvider.getInitialSnapshot());

        //calculate rating
        RatingCalculator ratingCalculator = new RatingOne(tangle);
        UnIterableMap<HashId, Integer> rating = ratingCalculator.calculate(transaction.getHash());

        //add 4 after the rating was calculated
        transaction4 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction4.store(tangle, snapshotProvider.getInitialSnapshot());

        for (int i=0; i < 100; i++) {
            //select
            Hash tip = walker.walk(transaction.getHash(), rating, (o -> true));

            Assert.assertTrue(tip != null);
            //log.info("selected tip: " + tip.toString());
            Assert.assertTrue(!transaction4.getHash().equals(tip));
        }
    }

    @Test
    public void showWalkDistributionAlphaHalf() throws Exception {

        //build a small tangle - 1,2,3,4 point to  transaction
        TransactionViewModel transaction, transaction1, transaction2, transaction3;
        transaction = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        transaction1 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction2 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction3 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());

        transaction.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction1.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction2.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction3.store(tangle, snapshotProvider.getInitialSnapshot());

        //calculate rating
        RatingCalculator ratingCalculator = new RatingOne(tangle);
        UnIterableMap<HashId, Integer> rating = ratingCalculator.calculate(transaction.getHash());
        //set a higher rate for transaction2
        rating.put(transaction2.getHash(), 10);

        Map<Hash, Integer> counters = new HashMap<>(rating.size());
        int iterations = 100;

        walker.setAlpha(0.3);
        for (int i=0; i < iterations; i++) {
            //select
            Hash tip = walker.walk(transaction.getHash(), rating, (o -> true));

            Assert.assertNotNull(tip);
            counters.put(tip, 1 + counters.getOrDefault(tip, 0));
        }

        for (Map.Entry<Hash, Integer> entry : counters.entrySet()) {
            log.info(entry.getKey().toString() + " : " + entry.getValue());
        }

        Assert.assertTrue(counters.get(transaction2.getHash()) > iterations / 2);
    }

    @Test
    public void showWalkDistributionAlphaZero() throws Exception {

        //build a small tangle - 1,2,3,4 point to  transaction
        TransactionViewModel transaction, transaction1, transaction2, transaction3, transaction4;
        transaction = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        transaction1 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction2 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction3 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());

        transaction.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction1.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction2.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction3.store(tangle, snapshotProvider.getInitialSnapshot());

        //calculate rating
        RatingCalculator ratingCalculator = new RatingOne(tangle);
       UnIterableMap<HashId, Integer> rating = ratingCalculator.calculate(transaction.getHash());
        //set a higher rate for transaction2
        rating.put(transaction2.getHash(), 10);

        //add 4 after the rating was calculated
        transaction4 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction4.store(tangle, snapshotProvider.getInitialSnapshot());

        Map<Hash, Integer> counters = new HashMap<>(rating.size());
        int iterations = 100;

        walker.setAlpha(0);
        for (int i=0; i < iterations; i++) {
            //select
            Hash tip = walker.walk(transaction.getHash(), rating, (o -> true));

            Assert.assertNotNull(tip);
            counters.put(tip, 1 + counters.getOrDefault(tip, 0));
        }

        for (Map.Entry<Hash, Integer> entry : counters.entrySet()) {
            log.info(entry.getKey().toString() + " : " + entry.getValue());
        }

        Assert.assertTrue(counters.get(transaction1.getHash()) > iterations / 6);
    }

    @Test
    public void testWalk() throws Exception {
        //build a small tangle
        TransactionViewModel transaction, transaction1, transaction2, transaction3, transaction4;
        transaction = new TransactionViewModel(getRandomTransactionTrits(), Hash.NULL_HASH);
        transaction1 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction2 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction1.getHash(),
                transaction1.getHash()), getRandomTransactionHash());
        transaction3 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction2.getHash(),
                transaction1.getHash()), getRandomTransactionHash());
        transaction4 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction2.getHash(),
                transaction3.getHash()), getRandomTransactionHash());
        transaction.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction1.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction2.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction3.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction4.store(tangle, snapshotProvider.getInitialSnapshot());

        //calculate rating
        RatingCalculator ratingCalculator = new RatingOne(tangle);
       UnIterableMap<HashId, Integer> rating = ratingCalculator.calculate(transaction.getHash());

        //reach the tips
        Hash tip = walker.walk(transaction.getHash(), rating, (o -> true));

        log.info("selected tip: " + tip.toString());
        Assert.assertEquals(tip, transaction4.getHash());
    }

    @Test
    public void testWalkDiamond() throws Exception {
        //build a small tangle
        TransactionViewModel transaction, transaction1, transaction2, transaction3;
        transaction = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        transaction1 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction2 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction3 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction1.getHash(),
                transaction2.getHash()), getRandomTransactionHash());
        transaction.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction1.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction2.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction3.store(tangle, snapshotProvider.getInitialSnapshot());

        //calculate rating
        RatingCalculator ratingCalculator = new RatingOne(tangle);
       UnIterableMap<HashId, Integer> rating = ratingCalculator.calculate(transaction.getHash());

        //reach the tips
        Hash tip = walker.walk(transaction.getHash(), rating, (o -> true));

        log.info("selected tip: " + tip.toString());
        Assert.assertEquals(tip, transaction3.getHash());
    }

    @Test
    public void testWalkChain() throws Exception {
        //build a small tangle
        TransactionViewModel transaction, transaction1, transaction2, transaction3, transaction4;
        transaction = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        transaction1 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(
                transaction.getHash(), transaction.getHash()), getRandomTransactionHash());
        transaction2 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(
                transaction1.getHash(), transaction1.getHash()), getRandomTransactionHash());
        transaction3 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(
                transaction2.getHash(), transaction2.getHash()), getRandomTransactionHash());
        transaction4 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(
                transaction3.getHash(), transaction3.getHash()), getRandomTransactionHash());
        transaction.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction1.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction2.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction3.store(tangle, snapshotProvider.getInitialSnapshot());
        transaction4.store(tangle, snapshotProvider.getInitialSnapshot());

        //calculate rating
        RatingCalculator ratingCalculator = new RatingOne(tangle);
       UnIterableMap<HashId, Integer> rating = ratingCalculator.calculate(transaction.getHash());

        //reach the tips
        Hash tip = walker.walk(transaction.getHash(), rating, (o -> true));

        log.info("selected tip: " + tip.toString());
        Assert.assertEquals(tip, transaction4.getHash());
    }

}

