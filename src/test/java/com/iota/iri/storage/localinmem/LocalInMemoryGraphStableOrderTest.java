package com.iota.iri.storage.localinmem;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.localinmemorygraph.LocalInMemoryGraphProvider;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.maven.repository.legacy.LegacyRepositorySystem;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.neo4j.cypher.internal.frontend.v2_3.ast.functions.Has;

import java.util.*;
import java.util.stream.Collectors;

import static com.iota.iri.controllers.TransactionViewModelTest.*;

public class LocalInMemoryGraphStableOrderTest {
    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static final TemporaryFolder dbFolder1 = new TemporaryFolder();
    private static final TemporaryFolder logFolder1 = new TemporaryFolder();
    private static Tangle tangle1;
    private static LocalInMemoryGraphProvider provider1;

    @AfterClass
    public static void tearDown() throws Exception {
        tangle1.shutdown();
        dbFolder.delete();
        BaseIotaConfig.getInstance().setStreamingGraphSupport(false);
    }

    @BeforeClass
    public static void setUp() throws Exception {
        BaseIotaConfig.getInstance().setStreamingGraphSupport(true);
        tangle1 = new Tangle();
        dbFolder.create();
        logFolder.create();
        dbFolder1.create();
        logFolder1.create();
        tangle1.addPersistenceProvider(new RocksDBPersistenceProvider(dbFolder.getRoot().getAbsolutePath(), logFolder
                .getRoot().getAbsolutePath(), 1000, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));
        provider1 = new LocalInMemoryGraphProvider("", tangle1);
        tangle1.addPersistenceProvider(provider1);
        tangle1.init();
        BaseIotaConfig.getInstance().setConfluxScoreAlgo("CUM_WEIGHT");
    }

    @Test
    public void testStableOrder() throws Exception {
        Hash a = getRandomTransactionHash();
        Hash b = getRandomTransactionHash();
        Hash c = getRandomTransactionHash();
        Hash d = getRandomTransactionHash();
        Hash e = getRandomTransactionHash();
        Hash f = getRandomTransactionHash();
        Hash g = getRandomTransactionHash();
        Hash h = getRandomTransactionHash();

        List<Hash> before = new LinkedList<>();
        List<Hash> after = new LinkedList<>();
        before.add(a);
        before.add(b);
        before.add(c);
        before.add(d);
        before.add(e);
        before.add(f);
        before.add(g);
        before.add(h);

        after.add(f);
        after.add(g);
        after.add(h);

        provider1.insertStableTotalOrder(before, after);

        List<Hash> stableOrder = provider1.getStableOrder();

        Assert.assertEquals(5, stableOrder.size());
        Assert.assertEquals(a, stableOrder.get(0));
        Assert.assertEquals(b, stableOrder.get(1));
        Assert.assertEquals(c, stableOrder.get(2));
        Assert.assertEquals(d, stableOrder.get(3));
        Assert.assertEquals(e, stableOrder.get(4));

        boolean isThrow = false;
        try {
            List<Hash> after1 = new LinkedList<>();
            after1.add(f);
            after1.add(h);
            provider1.insertStableTotalOrder(before, after1);
        } catch(RuntimeException ex) {
            isThrow = true;
        }
        Assert.assertEquals(true, isThrow);

        isThrow = false;
        try {
                Hash k = getRandomTransactionHash();
                List<Hash> after1 = new LinkedList<>();
                after1.add(k);
                after1.add(f);
                after1.add(g);
                after1.add(h);
                provider1.insertStableTotalOrder(before, after1);
            } catch(RuntimeException ex) {
                isThrow = true;
        }
        Assert.assertEquals(true, isThrow);

        isThrow = false;
        try {
                Hash k = getRandomTransactionHash();
                List<Hash> after1 = new LinkedList<>();
                after1.add(f);
                after1.add(g);
                after1.add(h);
                after1.add(k);
                provider1.insertStableTotalOrder(before, after1);
            } catch(RuntimeException ex) {
                isThrow = true;
        }
        Assert.assertEquals(true, isThrow);

        isThrow = false;
        try {
                Hash k = getRandomTransactionHash();
                List<Hash> after1 = new LinkedList<>();
                after1.add(f);
                after1.add(g);
                provider1.insertStableTotalOrder(before, after1);
            } catch(RuntimeException ex) {
                isThrow = true;
        }
        Assert.assertEquals(true, isThrow);
    }
}
