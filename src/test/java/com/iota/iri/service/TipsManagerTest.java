package com.iota.iri.service;

import com.iota.iri.LedgerValidator;
import com.iota.iri.Milestone;
import com.iota.iri.Snapshot;
import com.iota.iri.TransactionValidator;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.zmq.MessageQ;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.Buffer;
import java.util.*;

import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionHash;
import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionTrits;
import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch;

/**
 * Created by paul on 4/27/17.
 */
public class TipsManagerTest {

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static final String TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT =
            "tx%d cumulative weight is not as expected";
    private static Tangle tangle;
    private static TipsManager tipsManager;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Test
    public void capSum() {
        long a = 0, b, max = Long.MAX_VALUE / 2;
        for (b = 0; b < max; b += max / 100) {
            a = TipsManager.capSum(a, b, max);
            Assert.assertTrue("a should never go above max", a <= max);
        }
    }

    @BeforeClass
    public static void setUp() throws Exception {
        tangle = new Tangle();
        dbFolder.create();
        logFolder.create();
        tangle.addPersistenceProvider(new RocksDBPersistenceProvider(dbFolder.getRoot().getAbsolutePath(), logFolder
                .getRoot().getAbsolutePath(), 1000));
        tangle.init();
        TipsViewModel tipsViewModel = new TipsViewModel();
        MessageQ messageQ = new MessageQ(0, null, 1, false);
        TransactionRequester transactionRequester = new TransactionRequester(tangle, messageQ);
        TransactionValidator transactionValidator = new TransactionValidator(tangle, tipsViewModel,
                transactionRequester, messageQ);
        Milestone milestone = new Milestone(tangle, Hash.NULL_HASH, Snapshot.initialSnapshot.clone(),
                transactionValidator, true, messageQ);
        LedgerValidator ledgerValidator = new LedgerValidator(tangle, milestone, transactionRequester, messageQ);
        tipsManager = new TipsManager(tangle, ledgerValidator, transactionValidator, tipsViewModel, milestone, 15,
                messageQ);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        tangle.shutdown();
        dbFolder.delete();
    }

    @Test
    public void testCalculateCumulativeWeight() throws Exception {
        TransactionViewModel transaction, transaction1, transaction2, transaction3, transaction4;
        transaction = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        transaction1 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction2 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction1.getHash(),
                transaction1.getHash()), getRandomTransactionHash());
        transaction3 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction2.getHash(),
                transaction1.getHash()), getRandomTransactionHash());
        transaction4 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction2.getHash(),
                transaction3.getHash()), getRandomTransactionHash());
        transaction.store(tangle);
        transaction1.store(tangle);
        transaction2.store(tangle);
        transaction3.store(tangle);
        transaction4.store(tangle);
        Map<Buffer, Integer> txToCw = tipsManager.calculateCumulativeWeight(new HashSet<>(),
                transaction.getHash(), false, new HashSet<>());

        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 4),
                1, txToCw.get(transaction4.getHash().getSubHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 3),
                2, txToCw.get(transaction3.getHash().getSubHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 2),
                3, txToCw.get(transaction2.getHash().getSubHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 1),
                4, txToCw.get(transaction1.getHash().getSubHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 0),
                5, txToCw.get(transaction.getHash().getSubHash()).intValue());
    }

    @Test
    public void testCalculateCumulativeWeightDiamond() throws Exception {
        TransactionViewModel transaction, transaction1, transaction2, transaction3;
        transaction = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        transaction1 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction2 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction3 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction1.getHash(),
                transaction2.getHash()), getRandomTransactionHash());
        transaction.store(tangle);
        transaction1.store(tangle);
        transaction2.store(tangle);
        transaction3.store(tangle);
        log.debug("printing transaction in diamond shape \n                      {} \n{}  {}\n                      {}",
                transaction.getHash(), transaction1.getHash(), transaction2.getHash(), transaction3.getHash());
        Map<Buffer, Integer> txToCw = tipsManager.calculateCumulativeWeight(new HashSet<>(),
                transaction.getHash(), false, new HashSet<>());

        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 3),
                1, txToCw.get(transaction3.getHash().getSubHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 1),
                2, txToCw.get(transaction1.getHash().getSubHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 2),
                2, txToCw.get(transaction2.getHash().getSubHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 0),
                4, txToCw.get(transaction.getHash().getSubHash()).intValue());
    }

    @Test
    public void testCalculateCumulativeWeightLinear() throws Exception {
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
        transaction.store(tangle);
        transaction1.store(tangle);
        transaction2.store(tangle);
        transaction3.store(tangle);
        transaction4.store(tangle);

        log.info(String.format("Linear ordered hashes from tip %.4s, %.4s, %.4s, %.4s, %.4s", transaction4.getHash(),
                transaction3.getHash(), transaction2.getHash(), transaction1.getHash(), transaction.getHash()));

        Map<Buffer, Integer> txToCw = tipsManager.calculateCumulativeWeight(new HashSet<>(),
                transaction.getHash(), false, new HashSet<>());


        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 4),
                1, txToCw.get(transaction4.getHash().getSubHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 3),
                2, txToCw.get(transaction3.getHash().getSubHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 2),
                3, txToCw.get(transaction2.getHash().getSubHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 1),
                4, txToCw.get(transaction1.getHash().getSubHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 0),
                5, txToCw.get(transaction.getHash().getSubHash()).intValue());
    }

    @Test
    public void testCalculateCumulativeWeightAlon() throws Exception {
        TransactionViewModel transaction, transaction1, transaction2, transaction3, transaction4, transaction5,
                transaction6;
        transaction = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        transaction1 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(
                transaction.getHash(), transaction.getHash()), getRandomTransactionHash());
        transaction2 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(
                transaction.getHash(), transaction.getHash()), getRandomTransactionHash());
        transaction3 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(
                transaction.getHash(), transaction.getHash()), getRandomTransactionHash());
        transaction4 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(
                transaction.getHash(), transaction.getHash()), getRandomTransactionHash());
        transaction5 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(
                transaction3.getHash(), transaction2.getHash()), getRandomTransactionHash());
        transaction6 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(
                transaction4.getHash(), transaction5.getHash()), getRandomTransactionHash());

        transaction.store(tangle);
        transaction1.store(tangle);
        transaction2.store(tangle);
        transaction3.store(tangle);
        transaction4.store(tangle);
        transaction5.store(tangle);
        transaction6.store(tangle);

        log.debug("printing transactions in order \n{}\n{}\n{}\n{}\n{}\n{}\n{}",
                transaction.getHash(), transaction1.getHash(), transaction2.getHash(), transaction3.getHash(),
                transaction4, transaction5, transaction6);

        Map<Buffer, Integer> txToCw = tipsManager.calculateCumulativeWeight(new HashSet<>(),
                transaction.getHash(), false, new HashSet<>());

        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 6),
                1, txToCw.get(transaction6.getHash().getSubHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 5),
                2, txToCw.get(transaction5.getHash().getSubHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 4),
                2, txToCw.get(transaction4.getHash().getSubHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 3),
                3, txToCw.get(transaction3.getHash().getSubHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 2),
                3, txToCw.get(transaction2.getHash().getSubHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 1),
                1, txToCw.get(transaction1.getHash().getSubHash()).intValue());
        Assert.assertEquals(String.format(TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT, 0),
                7, txToCw.get(transaction.getHash().getSubHash()).intValue());
    }

    @Test
    public void cwCalculationSameAsLegacy() throws Exception {
        Hash[] hashes = new Hash[100];
        hashes[0] = getRandomTransactionHash();
        TransactionViewModel transactionViewModel1 = new TransactionViewModel(getRandomTransactionTrits(), hashes[0]);
        transactionViewModel1.store(tangle);
        //constant seed for consistent results
        Random random = new Random(181783497276652981L);
        for (int i = 1; i < hashes.length; i++) {
            hashes[i] = getRandomTransactionHash();
            TransactionViewModel transactionViewModel = new TransactionViewModel(
                    getRandomTransactionWithTrunkAndBranch(hashes[i - random.nextInt(i) - 1],
                            hashes[i - random.nextInt(i) - 1]), hashes[i]);
            transactionViewModel.store(tangle);
            log.debug(String.format("current transaction %.4s \n with trunk %.4s \n and branch %.4s", hashes[i],
                    transactionViewModel.getTrunkTransactionHash(),
                    transactionViewModel.getBranchTransactionHash()));
        }
        Map<Hash, Set<Hash>> ratings = new HashMap<>();
        updateApproversRecursively(hashes[0], ratings, new HashSet<>());
        Map<Buffer, Integer> txToCw = tipsManager.calculateCumulativeWeight(new HashSet<>(),
                hashes[0], false, new HashSet<>());

        Assert.assertEquals("missing txs from new calculation", ratings.size(), txToCw.size());
        ratings.forEach((hash, weight) -> {
            log.debug(String.format("tx %.4s has expected weight of %d", hash, weight.size()));
            Assert.assertEquals(
                    "new calculation weight is not as expected for hash " + hash,
                    weight.size(), txToCw.get(hash.getSubHash()).intValue());
        });
    }

    @Test
    public void testCalculateCommulativeWeightWithLeftBehind() throws Exception {
        TransactionViewModel transaction, transaction1, transaction2, transaction3, transaction4;
        transaction = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        transaction1 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction2 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction1.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction3 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction2.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction4 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction3.getHash(),
                transaction1.getHash()), getRandomTransactionHash());
        Set<Hash> approvedHashes = new HashSet<>();
        transaction.store(tangle);
        transaction1.store(tangle);
        approvedHashes.add(transaction2.getHash());
        transaction2.store(tangle);
        approvedHashes.add(transaction3.getHash());
        transaction3.store(tangle);
        transaction4.store(tangle);

        Map<Buffer, Integer> cumulativeWeight = tipsManager.calculateCumulativeWeight(approvedHashes,
                transaction.getHash(), true, new HashSet<>());

        log.info(cumulativeWeight.toString());
        String msg = "Cumulative weight is wrong for tx";
        Assert.assertEquals(msg + 4, 1, cumulativeWeight.get(transaction4.getHash().getSubHash()).intValue());
        Assert.assertEquals(msg + 3, 1, cumulativeWeight.get(transaction3.getHash().getSubHash()).intValue());
        Assert.assertEquals(msg + 2, 1, cumulativeWeight.get(transaction2.getHash().getSubHash()).intValue());
        Assert.assertEquals(msg + 1, 2, cumulativeWeight.get(transaction1.getHash().getSubHash()).intValue());
        Assert.assertEquals(msg + 0, 3, cumulativeWeight.get(transaction.getHash().getSubHash()).intValue());
    }

    //    @Test
    //To be removed once CI tests are ready
    public void testUpdateRatingsTime() throws Exception {
        int max = 100001;
        long time;
        List<Long> times = new LinkedList<>();
        for (int size = 1; size < max; size *= 10) {
            time = ratingTime(size);
            times.add(time);
        }
        Assert.assertEquals(1, 1);
    }

    private long ratingTime(int size) throws Exception {
        Hash[] hashes = new Hash[size];
        hashes[0] = getRandomTransactionHash();
        new TransactionViewModel(getRandomTransactionTrits(), hashes[0]).store(tangle);
        Random random = new Random();
        for (int i = 1; i < hashes.length; i++) {
            hashes[i] = getRandomTransactionHash();
            new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(hashes[i - random.nextInt(i) - 1],
                    hashes[i - random.nextInt(i) - 1]), hashes[i]).store(tangle);
        }
        Map<Hash, Long> ratings = new HashMap<>();
        long start = System.currentTimeMillis();
//        tipsManager.serialUpdateRatings(new Snapshot(Snapshot.initialSnapshot), hashes[0], ratings, new HashSet<>()
// , null);
        tipsManager.calculateCumulativeWeight(new HashSet<>(), hashes[0], false, new HashSet<>());
        long time = System.currentTimeMillis() - start;
        System.out.println(time);
        return time;
    }

    //Simple recursive algorithm that maps each tx hash to its approvers' hashes
    private static Set<Hash> updateApproversRecursively(Hash txHash, Map<Hash, Set<Hash>> txToApprovers,
            Set<Hash> analyzedTips) throws Exception {
        Set<Hash> approvers;
        if (analyzedTips.add(txHash)) {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, txHash);
            approvers = new HashSet<>(Collections.singleton(txHash));
            Set<Hash> approverHashes = transactionViewModel.getApprovers(tangle).getHashes();
            for (Hash approver : approverHashes) {
                approvers.addAll(updateApproversRecursively(approver, txToApprovers, analyzedTips));
            }
            txToApprovers.put(txHash, approvers);
        } else {
            if (txToApprovers.containsKey(txHash)) {
                approvers = txToApprovers.get(txHash);
            } else {
                approvers = new HashSet<>();
            }
        }
        return approvers;
    }
}