package com.iota.iri.service.tipselection.impl;

import com.iota.iri.LedgerValidator;
import com.iota.iri.Milestone;
import com.iota.iri.Snapshot;
import com.iota.iri.TransactionValidator;
import com.iota.iri.conf.Configuration;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.tipselection.TailFinder;
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
import java.util.*;

public class WalkerAlphaTest {
    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static Tangle tangle;
    private static WalkerAlpha walker;
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
        TipsViewModel tipsViewModel = new TipsViewModel();
        MessageQ messageQ = new MessageQ(0, null, 1, false);
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
        TailFinder tailFinder = new TailFinderImpl(tangle);

        walker = new WalkerAlpha(1, new Random(1), tangle, ledgerValidator, transactionValidator, messageQ, 15, tailFinder);
    }

    @Test
    public void testSelectOnlyInRating() {

        //build rating list
        Map<Hash, Integer> rating = new HashMap<>();
        rating.put(new Hash("A"), 1);
        rating.put(new Hash("B"), 1);
        rating.put(new Hash("C"), 1);
        //D missing on purpose

        //build approvers list
        Set<Hash> approvers = new HashSet<>();
        approvers.add(new Hash("A"));
        approvers.add(new Hash("B"));
        approvers.add(new Hash("C"));
        approvers.add(new Hash("D"));

        for (int i=0; i < 10; i++) {
        //select
        Optional<Hash> next = walker.select(rating, approvers);

        Assert.assertTrue(next.isPresent());
        //log.info("selected next step: " + next.get().toString());

        Assert.assertTrue(!(new Hash("D")).toString().equals(next.get().toString()));
        }
    }

    @Test
    public void showSelectDistributionAlphaHalf() {

        //build rating list
        Map<Hash, Integer> rating = new HashMap<>();
        rating.put(new Hash("A"), 1);
        rating.put(new Hash("B"), 10);
        rating.put(new Hash("C"), 1);
        //D missing on purpose

        //build approvers list
        Set<Hash> approvers = new HashSet<>();
        approvers.add(new Hash("A"));
        approvers.add(new Hash("B"));
        approvers.add(new Hash("C"));
        approvers.add(new Hash("D"));

        Map<Hash, Integer> counters = new HashMap<>(rating.size());
        int iterations = 10000;

        walker.setAlpha(0.3);
        for (int i=0; i < iterations; i++) {
            //select
            Optional<Hash> next = walker.select(rating, approvers);

            Assert.assertTrue(next.isPresent());
            counters.put(next.get(), 1 + counters.getOrDefault(next.get(), 0));
        }

        for (Map.Entry<Hash, Integer> entry : counters.entrySet()) {
            log.info(entry.getKey().toString() + " : " + entry.getValue());
        }

        Assert.assertTrue(counters.get(new Hash("B")) > iterations / 2);
    }

    @Test
    public void showSelectDistributionAlphaZero() {

        //build rating list
        Map<Hash, Integer> rating = new HashMap<>();
        rating.put(new Hash("A"), 1);
        rating.put(new Hash("B"), 10);
        rating.put(new Hash("C"), 1);
        //D missing on purpose

        //build approvers list
        Set<Hash> approvers = new HashSet<>();
        approvers.add(new Hash("A"));
        approvers.add(new Hash("B"));
        approvers.add(new Hash("C"));
        approvers.add(new Hash("D"));

        Map<Hash, Integer> counters = new HashMap<>(rating.size());
        int iterations = 10000;

        walker.setAlpha(0);
        for (int i=0; i < iterations; i++) {
            //select
            Optional<Hash> next = walker.select(rating, approvers);

            Assert.assertTrue(next.isPresent());
            counters.put(next.get(), 1 + counters.getOrDefault(next.get(), 0));
        }

        for (Map.Entry<Hash, Integer> entry : counters.entrySet()) {
            log.info(entry.getKey().toString() + " : " + entry.getValue());
        }

        Assert.assertTrue(counters.get(new Hash("A")) > iterations / 6);
    }
}

