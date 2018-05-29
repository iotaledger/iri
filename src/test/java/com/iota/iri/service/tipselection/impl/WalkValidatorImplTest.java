package com.iota.iri.service.tipselection.impl;

import com.iota.iri.LedgerValidator;
import com.iota.iri.Milestone;
import com.iota.iri.Snapshot;
import com.iota.iri.TransactionValidator;
import com.iota.iri.conf.Configuration;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.tipselection.WalkValidator;
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

import static com.iota.iri.controllers.TransactionViewModelTest.*;

public class WalkValidatorImplTest {

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static Tangle tangle;
    private static WalkValidator walkValidator;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

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
        MessageQ messageQ = new MessageQ(0, null, 1, false);

        TipsViewModel tipsViewModel = new TipsViewModel();
        TransactionRequester transactionRequester = new TransactionRequester(tangle, messageQ);
        TransactionValidator transactionValidator = new TransactionValidator(tangle, tipsViewModel, transactionRequester,
                messageQ, Long.parseLong(Configuration.GLOBAL_SNAPSHOT_TIME));
        int milestoneStartIndex = Integer.parseInt(Configuration.MAINNET_MILESTONE_START_INDEX);
        int numOfKeysInMilestone = Integer.parseInt(Configuration.MAINNET_NUM_KEYS_IN_MILESTONE);
        Milestone milestone = new Milestone(tangle, Hash.NULL_HASH, Snapshot.init(
                Configuration.MAINNET_SNAPSHOT_FILE, Configuration.MAINNET_SNAPSHOT_SIG_FILE, false).clone(),
                transactionValidator, false, messageQ, numOfKeysInMilestone,
                milestoneStartIndex, true);
        LedgerValidator ledgerValidator = new LedgerValidator(tangle, milestone, transactionRequester, messageQ);

        walkValidator  = new WalkValidatorImpl(tangle, ledgerValidator, transactionValidator, milestone, 15);
    }


    @Test
    public void shouldPassValidation() throws Exception {
        //build a small tangle - 1,2,3,4 point to  transaction
        TransactionViewModel transaction;
        transaction = new TransactionViewModel(getRandomTransactionWithTrunkAndBranchValidBundle(Hash.NULL_HASH,
                Hash.NULL_HASH), getRandomTransactionHash());
        transaction.store(tangle);

        Assert.assertTrue(walkValidator.isValid(transaction.getHash()));
    }

    @Test
    public void shouldFailValidation() throws Exception {
        //build a small tangle - 1,2,3,4 point to  transaction
        TransactionViewModel transaction;
        transaction = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(Hash.NULL_HASH,
                Hash.NULL_HASH), getRandomTransactionHash());
        transaction.store(tangle);

        Assert.assertFalse(walkValidator.isValid(transaction.getHash()));
    }

}