package com.iota.iri.service.tipselection.impl;

import com.iota.iri.validator.LedgerValidator;
import com.iota.iri.validator.MilestoneTracker;
import com.iota.iri.Iota;
import com.iota.iri.TransactionTestUtils;
import com.iota.iri.validator.TransactionValidator;
import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.conf.TipSelConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.controllers.TransactionViewModelTest;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.HashSet;

@RunWith(MockitoJUnitRunner.class)
public class WalkValidatorImplTest {

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static Tangle tangle;
    private TipSelConfig config = new MainnetConfig();
    @Mock
    private LedgerValidator ledgerValidator;
    @Mock
    private TransactionValidator transactionValidator;
    @Mock
    private MilestoneTracker milestoneTrackerTracker;

    @AfterClass
    public static void tearDown() throws Exception {
        tangle.shutdown();
        dbFolder.delete();
    }

    @BeforeClass
    public static void setUp() throws Exception {
        tangle = new Tangle();
        dbFolder.create();
        logFolder.create();
        tangle.addPersistenceProvider( new RocksDBPersistenceProvider(
                dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),1000,
                Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));
        tangle.init();
    }

    @Test
    public void shouldPassValidation() throws Exception {
        int depth = 15;
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(0);
        tx.updateSolid(true);
        tx.store(tangle);
        Hash hash = tx.getHash();
        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(true);
        milestoneTrackerTracker.latestSolidSubtangleMilestoneIndex = depth;

        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerValidator, milestoneTrackerTracker, config);
        Assert.assertTrue("Validation failed", walkValidator.isValid(hash));
    }

    @Test
    public void failOnTxType() throws Exception {
        int depth = 15;
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(0);
        tx.store(tangle);
        Hash hash = tx.getTrunkTransactionHash();
        tx.updateSolid(true);
        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(true);
        milestoneTrackerTracker.latestSolidSubtangleMilestoneIndex = depth;

        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerValidator,
        milestoneTrackerTracker, config);
        Assert.assertFalse("Validation succeded but should have failed since tx is missing", walkValidator.isValid(hash));
    }

    @Test
    public void failOnTxIndex() throws Exception {
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(2);
        tx.store(tangle);
        Hash hash = tx.getHash();
        tx.updateSolid(true);
        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(true);
        milestoneTrackerTracker.latestSolidSubtangleMilestoneIndex = Integer.MAX_VALUE;

        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerValidator,
        milestoneTrackerTracker, config);
        Assert.assertFalse("Validation succeded but should have failed since we are not on a tail", walkValidator.isValid(hash));
    }

    @Test
    public void failOnSolid() throws Exception {
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(0);
        tx.store(tangle);
        Hash hash = tx.getHash();
        tx.updateSolid(false);
        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(true);
        milestoneTrackerTracker.latestSolidSubtangleMilestoneIndex = Integer.MAX_VALUE;

        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerValidator,
        milestoneTrackerTracker, config);
        Assert.assertFalse("Validation succeded but should have failed since tx is not solid",
                walkValidator.isValid(hash));
    }

    @Test
    public void failOnBelowMaxDepthDueToOldMilestone() throws Exception {
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(0);
        tx.store(tangle);
        tx.setSnapshot(tangle, 2);
        Hash hash = tx.getHash();
        tx.updateSolid(true);
        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(true);
        milestoneTrackerTracker.latestSolidSubtangleMilestoneIndex = Integer.MAX_VALUE;
        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerValidator,
        milestoneTrackerTracker, config);
        Assert.assertFalse("Validation succeeded but should have failed tx is below max depth",
                walkValidator.isValid(hash));
    }

    @Test
    public void belowMaxDepthWithFreshMilestone() throws Exception {
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(0);
        tx.store(tangle);
        tx.setSnapshot(tangle, 92);
        Hash hash = tx.getHash();
        for (int i = 0; i < 4 ; i++) {
            tx = new TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(hash, hash), TransactionViewModelTest.getRandomTransactionHash());
            TransactionTestUtils.setLastIndex(tx,0);
            TransactionTestUtils.setCurrentIndex(tx,0);
            tx.updateSolid(true);
            hash = tx.getHash();
            tx.store(tangle);
        }
        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(true);
        milestoneTrackerTracker.latestSolidSubtangleMilestoneIndex = 100;
        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerValidator,
        milestoneTrackerTracker, config);
        Assert.assertTrue("Validation failed but should have succeeded since tx is above max depth",
                walkValidator.isValid(hash));
    }

    @Test
    public void failBelowMaxDepthWithFreshMilestoneDueToLongChain() throws Exception {
        final int maxAnalyzedTxs = config.getBelowMaxDepthTransactionLimit();
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(0);
        tx.store(tangle);
        tx.setSnapshot(tangle, 92);
        Hash hash = tx.getHash();
        for (int i = 0; i < maxAnalyzedTxs ; i++) {
            tx = new TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(hash, hash),
                    TransactionViewModelTest.getRandomTransactionHash());
            TransactionTestUtils.setLastIndex(tx,0);
            TransactionTestUtils.setCurrentIndex(tx,0);
            hash = tx.getHash();
            tx.updateSolid(true);
            tx.store(tangle);
        }
        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(true);
        milestoneTrackerTracker.latestSolidSubtangleMilestoneIndex = 100;
        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerValidator,
        milestoneTrackerTracker, config);
        Assert.assertFalse("Validation succeeded but should have failed since tx is below max depth",
                walkValidator.isValid(hash));
    }

    @Test
    public void belowMaxDepthOnGenesis() throws Exception {
        TransactionViewModel tx = null;
        final int maxAnalyzedTxs = config.getBelowMaxDepthTransactionLimit();
        Hash hash = Hash.NULL_HASH;
        for (int i = 0; i < maxAnalyzedTxs - 2 ; i++) {
            tx = new TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(hash, hash), TransactionViewModelTest.getRandomTransactionHash());
            TransactionTestUtils.setLastIndex(tx,0);
            TransactionTestUtils.setCurrentIndex(tx,0);
            tx.updateSolid(true);
            hash = tx.getHash();
            tx.store(tangle);
        }
        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), tx.getHash()))
                .thenReturn(true);
        milestoneTrackerTracker.latestSolidSubtangleMilestoneIndex = 15;
        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerValidator,
        milestoneTrackerTracker, config);
        Assert.assertTrue("Validation failed but should have succeeded. We didn't exceed the maximal amount of" +
                        "transactions that may be analyzed.",
                walkValidator.isValid(tx.getHash()));
    }

    @Test
    public void failBelowMaxDepthOnGenesisDueToLongChain() throws Exception {
        final int maxAnalyzedTxs = config.getBelowMaxDepthTransactionLimit();
        TransactionViewModel tx = null;
        Hash hash = Hash.NULL_HASH;
        for (int i = 0; i < maxAnalyzedTxs; i++) {
            tx = new TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(hash, hash),
                    TransactionViewModelTest.getRandomTransactionHash());
            TransactionTestUtils.setLastIndex(tx,0);
            TransactionTestUtils.setCurrentIndex(tx,0);
            tx.updateSolid(true);
            tx.store(tangle);
            hash = tx.getHash();
        }
        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), tx.getHash()))
                .thenReturn(true);
        milestoneTrackerTracker.latestSolidSubtangleMilestoneIndex = 17;
        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerValidator,
        milestoneTrackerTracker, config);
        Assert.assertFalse("Validation succeeded but should have failed. We exceeded the maximal amount of" +
                        "transactions that may be analyzed.",
                walkValidator.isValid(tx.getHash()));
    }

    @Test
    public void failOnInconsistency() throws Exception {
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(0);
        tx.store(tangle);
        Hash hash = tx.getHash();
        tx.updateSolid(true);
        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(false);
        milestoneTrackerTracker.latestSolidSubtangleMilestoneIndex = Integer.MAX_VALUE;

        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerValidator,
        milestoneTrackerTracker, config);
        Assert.assertFalse("Validation succeded but should have failed due to inconsistent ledger state",
                walkValidator.isValid(hash));
    }

    @Test
    public void dontMarkWrongTxsAsBelowMaxDepth() throws Exception {
        final int maxAnalyzedTxs = config.getBelowMaxDepthTransactionLimit();
        TransactionViewModel tx1 = TransactionTestUtils.createBundleHead(0);
        tx1.store(tangle);
        tx1.setSnapshot(tangle, 92);

        TransactionViewModel txBad = TransactionTestUtils.createBundleHead(0);
        txBad.store(tangle);
        txBad.setSnapshot(tangle, 10);

        TransactionViewModel tx2 = new TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(tx1.getHash(), tx1.getHash()),
                TransactionViewModelTest.getRandomTransactionHash());
        TransactionTestUtils.setLastIndex(tx2,0);
        TransactionTestUtils.setCurrentIndex(tx2,0);
        tx2.updateSolid(true);
        tx2.store(tangle);

        TransactionViewModel tx3 = new TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(tx1.getHash(), txBad.getHash()),
                TransactionViewModelTest.getRandomTransactionHash());
        TransactionTestUtils.setLastIndex(tx3,0);
        TransactionTestUtils.setCurrentIndex(tx3,0);
        tx3.updateSolid(true);
        tx3.store(tangle);

        TransactionViewModel tx4 = new TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(tx2.getHash(), tx3.getHash()),
                TransactionViewModelTest.getRandomTransactionHash());
        TransactionTestUtils.setLastIndex(tx4,0);
        TransactionTestUtils.setCurrentIndex(tx4,0);
        tx4.updateSolid(true);
        tx4.store(tangle);

        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), tx4.getHash()))
                .thenReturn(true);
        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), tx2.getHash()))
                .thenReturn(true);

        milestoneTrackerTracker.latestSolidSubtangleMilestoneIndex = 100;
        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerValidator,
        milestoneTrackerTracker, config);
        Assert.assertFalse("Validation of tx4 succeeded but should have failed since tx is below max depth",
                walkValidator.isValid(tx4.getHash()));
        Assert.assertTrue("Validation of tx2 failed but should have succeeded since tx is above max depth",
                walkValidator.isValid(tx2.getHash()));
    }

    @Test
    public void allowConfirmedTxToPassBelowMaxDepthAfterMilestoneConfirmation() throws Exception {
        final int maxAnalyzedTxs = config.getBelowMaxDepthTransactionLimit();
        TransactionViewModel tx1 = TransactionTestUtils.createBundleHead(0);
        tx1.store(tangle);
        tx1.setSnapshot(tangle, 92);

        TransactionViewModel txBad = TransactionTestUtils.createBundleHead(0);
        txBad.store(tangle);
        txBad.setSnapshot(tangle, 10);

        TransactionViewModel tx2 = new TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(tx1.getHash(), tx1.getHash()),
                TransactionViewModelTest.getRandomTransactionHash());
        TransactionTestUtils.setLastIndex(tx2,0);
        TransactionTestUtils.setCurrentIndex(tx2,0);
        tx2.updateSolid(true);
        tx2.store(tangle);

        TransactionViewModel tx3 = new TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(tx1.getHash(), txBad.getHash()),
                TransactionViewModelTest.getRandomTransactionHash());
        TransactionTestUtils.setLastIndex(tx3,0);
        TransactionTestUtils.setCurrentIndex(tx3,0);
        tx3.updateSolid(true);
        tx3.store(tangle);

        TransactionViewModel tx4 = new TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(tx2.getHash(), tx3.getHash()),
                TransactionViewModelTest.getRandomTransactionHash());
        TransactionTestUtils.setLastIndex(tx4,0);
        TransactionTestUtils.setCurrentIndex(tx4,0);
        tx4.updateSolid(true);
        tx4.store(tangle);

        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), tx4.getHash()))
                .thenReturn(true);

        milestoneTrackerTracker.latestSolidSubtangleMilestoneIndex = 100;
        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerValidator,
        milestoneTrackerTracker, config);
        Assert.assertFalse("Validation of tx4 succeeded but should have failed since tx is below max depth",
                walkValidator.isValid(tx4.getHash()));

        //Now assume milestone 99 confirmed tx3
        tx3.setSnapshot(tangle, 99);

        Assert.assertTrue("Validation of tx4 failed but should have succeeded since tx is above max depth",
                walkValidator.isValid(tx4.getHash()));
    }
}
