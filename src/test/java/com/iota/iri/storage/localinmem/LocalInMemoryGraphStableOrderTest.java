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
    }

    @Test
    public void testStableOrder() throws Exception {
        Hash A = getRandomTransactionHash();
        Hash B = getRandomTransactionHash();
        Hash C = getRandomTransactionHash();
        Hash D = getRandomTransactionHash();
        Hash E = getRandomTransactionHash();
        Hash F = getRandomTransactionHash();
        Hash G = getRandomTransactionHash();
        Hash H = getRandomTransactionHash();

        List<Hash> before = new LinkedList<>();
        List<Hash> after = new LinkedList<>();
        before.add(A);
        before.add(B);
        before.add(C);
        before.add(D);
        before.add(E);
        before.add(F);
        before.add(G);
        before.add(H);

        after.add(F);
        after.add(G);
        after.add(H);

        provider1.insertStableTotalOrder(before, after);

        List<Hash> stableOrder = provider1.getStableOrder();

        Assert.assertEquals(5, stableOrder.size());
        Assert.assertEquals(A, stableOrder.get(0));
        Assert.assertEquals(B, stableOrder.get(1));
        Assert.assertEquals(C, stableOrder.get(2));
        Assert.assertEquals(D, stableOrder.get(3));
        Assert.assertEquals(E, stableOrder.get(4));

        boolean isThrow = false;
        try {
            List<Hash> after1 = new LinkedList<>();
            after1.add(F);
            after1.add(H);
            provider1.insertStableTotalOrder(before, after1);
        } catch(RuntimeException e) {
            isThrow = true;
        }
        Assert.assertEquals(true, isThrow);

        isThrow = false;
        try {
                Hash K = getRandomTransactionHash();
                List<Hash> after1 = new LinkedList<>();
                after1.add(K);
                after1.add(F);
                after1.add(G);
                after1.add(H);
                provider1.insertStableTotalOrder(before, after1);
            } catch(RuntimeException e) {
                isThrow = true;
        }
        Assert.assertEquals(true, isThrow);

        isThrow = false;
        try {
                Hash K = getRandomTransactionHash();
                List<Hash> after1 = new LinkedList<>();
                after1.add(F);
                after1.add(G);
                after1.add(H);
                after1.add(K);
                provider1.insertStableTotalOrder(before, after1);
            } catch(RuntimeException e) {
                isThrow = true;
        }
        Assert.assertEquals(true, isThrow);

        isThrow = false;
        try {
                Hash K = getRandomTransactionHash();
                List<Hash> after1 = new LinkedList<>();
                after1.add(F);
                after1.add(G);
                provider1.insertStableTotalOrder(before, after1);
            } catch(RuntimeException e) {
                isThrow = true;
        }
        Assert.assertEquals(true, isThrow);
    }
}
