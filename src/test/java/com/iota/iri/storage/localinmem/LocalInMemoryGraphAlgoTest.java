package com.iota.iri.storage.localinmem;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.localinmemorygraph.LocalInMemoryGraphProvider;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.neo4j.cypher.internal.frontend.v2_3.ast.functions.Has;

import java.util.*;
import java.util.stream.Collectors;

import static com.iota.iri.controllers.TransactionViewModelTest.*;

public class LocalInMemoryGraphAlgoTest {
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
/*
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
*/
    @Test
    public void testInduceSubGraph() throws Exception {
        TransactionViewModel A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P;
        A = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        B = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(A.getHash(),
                A.getHash()), getRandomTransactionHash());
        D = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(B.getHash(),
                B.getHash()), getRandomTransactionHash());
        C = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(D.getHash(),
                B.getHash()), getRandomTransactionHash());
        E = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(D.getHash(),
                B.getHash()), getRandomTransactionHash());
        F = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(E.getHash(),
                B.getHash()), getRandomTransactionHash());
        G = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(D.getHash(),
                C.getHash()), getRandomTransactionHash());
        H = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(E.getHash(),
                F.getHash()), getRandomTransactionHash());
        I = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(G.getHash(),
                H.getHash()), getRandomTransactionHash());
        J = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(C.getHash(),
                G.getHash()), getRandomTransactionHash());
        K = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(G.getHash(),
                J.getHash()), getRandomTransactionHash());
        L = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(K.getHash(),
                J.getHash()), getRandomTransactionHash());
        M = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(I.getHash(),
                H.getHash()), getRandomTransactionHash());
        N = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(M.getHash(),
                I.getHash()), getRandomTransactionHash());
        O = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(A.getHash(),
                B.getHash()), getRandomTransactionHash());
        P = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(O.getHash(),
                C.getHash()), getRandomTransactionHash());

        HashMap<Hash, String> tag = new HashMap<Hash, String>();

        tag.put(A.getHash(), "A");
        tag.put(B.getHash(), "B");
        tag.put(C.getHash(), "C");
        tag.put(D.getHash(), "D");
        tag.put(E.getHash(), "E");
        tag.put(F.getHash(), "F");
        tag.put(G.getHash(), "G");
        tag.put(H.getHash(), "H");
        tag.put(I.getHash(), "I");
        tag.put(J.getHash(), "J");
        tag.put(K.getHash(), "K");
        tag.put(L.getHash(), "L");
        tag.put(M.getHash(), "M");
        tag.put(N.getHash(), "N");
        tag.put(O.getHash(), "O");
        tag.put(P.getHash(), "P");

        provider1.setNameMap(tag);

        A.store(tangle1);
        B.store(tangle1);
        C.store(tangle1);
        D.store(tangle1);
        E.store(tangle1);
        F.store(tangle1);
        G.store(tangle1);
        H.store(tangle1);
        I.store(tangle1);
        J.store(tangle1);
        K.store(tangle1);
        L.store(tangle1);
        M.store(tangle1);
        N.store(tangle1);
        O.store(tangle1);
        P.store(tangle1);

        List<Hash> order = provider1.totalTopOrder();
        for(int i=0; i<order.size(); i++) {
            System.out.println(tag.get(order.get(i)));
        }
        /*provider1.induceGraphFromAncestor(G.getHash());

        List<Hash> order1 = provider1.totalTopOrder();
        for(int i=0; i<order.size(); i++) {
            System.out.println(tag.get(order1.get(i)));
        }*/
    }
}
