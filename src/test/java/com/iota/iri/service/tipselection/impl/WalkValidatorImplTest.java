package com.iota.iri.service.tipselection.impl;

import com.iota.iri.LedgerValidator;
import com.iota.iri.Milestone;
import com.iota.iri.TransactionTestUtils;
import com.iota.iri.TransactionValidator;
import com.iota.iri.controllers.TransactionViewModel;
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
    @Mock
    private LedgerValidator ledgerValidator;
    @Mock
    private TransactionValidator transactionValidator;
    @Mock
    private Milestone milestoneTracker;

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
        tangle.addPersistenceProvider(new RocksDBPersistenceProvider(dbFolder.getRoot().getAbsolutePath(), logFolder
                .getRoot().getAbsolutePath(), 1000));
        tangle.init();
    }

    @Test
    public void shouldPassValidation() throws Exception {
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(0);
        tx.store(tangle);
        Hash hash = tx.getHash();
        Mockito.when(transactionValidator.checkSolidity(hash, false))
                .thenReturn(true);
        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(true);
        milestoneTracker.latestSolidSubtangleMilestoneIndex = Integer.MAX_VALUE;

        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerValidator, transactionValidator, milestoneTracker, 15);
        Assert.assertTrue("Validation failed", walkValidator.isValid(hash));
    }

    @Test
    public void failOnTxType() throws Exception {
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(0);
        tx.store(tangle);
        Hash hash = tx.getTrunkTransactionHash();
        Mockito.when(transactionValidator.checkSolidity(hash, false))
                .thenReturn(true);
        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(true);
        milestoneTracker.latestSolidSubtangleMilestoneIndex = Integer.MAX_VALUE;

        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerValidator, transactionValidator, milestoneTracker, 15);
        Assert.assertFalse("Validation succeded but should have failed since tx is missing", walkValidator.isValid(hash));
    }

    @Test
    public void failOnTxIndex() throws Exception {
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(2);
        tx.store(tangle);
        Hash hash = tx.getHash();
        Mockito.when(transactionValidator.checkSolidity(hash, false))
                .thenReturn(true);
        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(true);
        milestoneTracker.latestSolidSubtangleMilestoneIndex = Integer.MAX_VALUE;

        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerValidator, transactionValidator, milestoneTracker, 15);
        Assert.assertFalse("Validation succeded but should have failed since we are not on a tail", walkValidator.isValid(hash));
    }

    @Test
    public void failOnSolid() throws Exception {
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(0);
        tx.store(tangle);
        Hash hash = tx.getHash();
        Mockito.when(transactionValidator.checkSolidity(hash, false))
                .thenReturn(false);
        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(true);
        milestoneTracker.latestSolidSubtangleMilestoneIndex = Integer.MAX_VALUE;

        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerValidator, transactionValidator, milestoneTracker, 15);
        Assert.assertFalse("Validation succeded but should have failed since tx is not solid",
                walkValidator.isValid(hash));
    }

    @Test
    public void failOnBelowMaxDepth() throws Exception {
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(0);
        tx.store(tangle);
        tx.setSnapshot(tangle, 2);
        Hash hash = tx.getHash();
        Mockito.when(transactionValidator.checkSolidity(hash, false))
                .thenReturn(true);
        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(true);
        milestoneTracker.latestSolidSubtangleMilestoneIndex = Integer.MAX_VALUE;
        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerValidator, transactionValidator, milestoneTracker, 15);
        Assert.assertFalse("Validation succeded but should have failed tx is below max depth",
                walkValidator.isValid(hash));
    }

    @Test
    public void failOnInconsistency() throws Exception {
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(0);
        tx.store(tangle);
        Hash hash = tx.getHash();
        Mockito.when(transactionValidator.checkSolidity(hash, false))
                .thenReturn(true);
        Mockito.when(ledgerValidator.updateDiff(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(false);
        milestoneTracker.latestSolidSubtangleMilestoneIndex = Integer.MAX_VALUE;

        WalkValidatorImpl walkValidator = new WalkValidatorImpl(tangle, ledgerValidator, transactionValidator, milestoneTracker, 15);
        Assert.assertFalse("Validation succeded but should have failed due to inconsistent ledger state",
                walkValidator.isValid(hash));
    }
}