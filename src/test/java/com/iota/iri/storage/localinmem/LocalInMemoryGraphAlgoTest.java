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
        Assert.assertEquals("A", tag.get(order.get(0)));
        Assert.assertEquals("B", tag.get(order.get(1)));
        Assert.assertEquals("D", tag.get(order.get(2)));
        Assert.assertEquals("C", tag.get(order.get(3)));
        Assert.assertEquals("G", tag.get(order.get(4)));
        Assert.assertEquals("E", tag.get(order.get(5)));
        Assert.assertEquals("F", tag.get(order.get(6)));
        Assert.assertEquals("H", tag.get(order.get(7)));
        Assert.assertEquals("I", tag.get(order.get(8)));
        Assert.assertEquals("M", tag.get(order.get(9)));
        Assert.assertEquals("N", tag.get(order.get(10)));

        provider1.buildTempGraphs(order, G.getHash());
        Assert.assertEquals(provider1.subGraph.size(), 7);

        Assert.assertEquals(2, provider1.subGraph.get(L.getHash()).size());
        Assert.assertEquals(2, provider1.subGraph.get(K.getHash()).size());
        Assert.assertEquals(1, provider1.subGraph.get(J.getHash()).size());
        Assert.assertEquals(2, provider1.subGraph.get(N.getHash()).size());
        Assert.assertEquals(1, provider1.subGraph.get(M.getHash()).size());
        Assert.assertEquals(1, provider1.subGraph.get(I.getHash()).size());
        Assert.assertEquals(2, provider1.subGraph.get(G.getHash()).size());

        Assert.assertEquals(null, provider1.subRevGraph.get(L.getHash()));
        Assert.assertEquals(1, provider1.subRevGraph.get(K.getHash()).size());
        Assert.assertEquals(2, provider1.subRevGraph.get(J.getHash()).size());
        Assert.assertEquals(null, provider1.subRevGraph.get(N.getHash()));
        Assert.assertEquals(1, provider1.subRevGraph.get(M.getHash()).size());
        Assert.assertEquals(2, provider1.subRevGraph.get(I.getHash()).size());
        Assert.assertEquals(3, provider1.subRevGraph.get(G.getHash()).size());
        Assert.assertEquals(1, provider1.subRevGraph.get(C.getHash()).size());
        Assert.assertEquals(1, provider1.subRevGraph.get(D.getHash()).size());

        
        Assert.assertEquals(K.getHash(), provider1.subParentGraph.get(L.getHash()));
        Assert.assertEquals(G.getHash(), provider1.subParentGraph.get(K.getHash()));
        Assert.assertEquals(M.getHash(), provider1.subParentGraph.get(N.getHash()));
        Assert.assertEquals(I.getHash(), provider1.subParentGraph.get(M.getHash()));
        Assert.assertEquals(G.getHash(), provider1.subParentGraph.get(I.getHash()));
        Assert.assertEquals(D.getHash(), provider1.subParentGraph.get(G.getHash()));

        
        Assert.assertEquals(2, provider1.subParentRevGraph.get(G.getHash()).size());
        Assert.assertEquals(1, provider1.subParentRevGraph.get(K.getHash()).size());
        Assert.assertEquals(1, provider1.subParentRevGraph.get(I.getHash()).size());
        Assert.assertEquals(1, provider1.subParentRevGraph.get(M.getHash()).size());
        Assert.assertEquals(null, provider1.subParentRevGraph.get(N.getHash()));
        Assert.assertEquals(null, provider1.subParentRevGraph.get(L.getHash()));
        Assert.assertEquals(null, provider1.subParentRevGraph.get(J.getHash()));

        provider1.reserveTempGraphs(order, G.getHash());
 
        Stack<Hash> ancestors = new Stack<>();
        ancestors.add(G.getHash());
        provider1.storeAncestors(ancestors);

        provider1.shiftTempGraphs();
       
        //System.out.println(tag.get(provider1.getGenesis()));
        Assert.assertEquals(7.0, provider1.getScore(G.getHash()), 0.001);
        Assert.assertEquals(2.0, provider1.getScore(K.getHash()), 0.001);
        Assert.assertEquals(3.0, provider1.getScore(J.getHash()), 0.001);
        Assert.assertEquals(1.0, provider1.getScore(L.getHash()), 0.001);
        Assert.assertEquals(2.0, provider1.getScore(M.getHash()), 0.001);
        Assert.assertEquals(1.0, provider1.getScore(N.getHash()), 0.001);
        Assert.assertEquals(3.0, provider1.getScore(I.getHash()), 0.001);

        Assert.assertEquals(6.0, provider1.getParentScore(G.getHash()), 0.001);
        Assert.assertEquals(2.0, provider1.getParentScore(K.getHash()), 0.001);
        Assert.assertEquals(1.0, provider1.getParentScore(J.getHash()), 0.001);
        Assert.assertEquals(1.0, provider1.getParentScore(L.getHash()), 0.001);
        Assert.assertEquals(2.0, provider1.getParentScore(M.getHash()), 0.001);
        Assert.assertEquals(1.0, provider1.getParentScore(N.getHash()), 0.001);
        Assert.assertEquals(3.0, provider1.getParentScore(I.getHash()), 0.001);

        List<Hash> order1 = provider1.totalTopOrder();
        Assert.assertEquals("G", tag.get(order1.get(0)));
        Assert.assertEquals("E", tag.get(order1.get(1)));
        Assert.assertEquals("F", tag.get(order1.get(2)));
        Assert.assertEquals("H", tag.get(order1.get(3)));
        Assert.assertEquals("I", tag.get(order1.get(4)));
        Assert.assertEquals("M", tag.get(order1.get(5)));
        Assert.assertEquals("N", tag.get(order1.get(6)));
       
        provider1.insertStableTotalOrder(order, order1);

        List<Hash> stable = provider1.getStableOrder();
        Assert.assertEquals("A", tag.get(stable.get(0)));
        Assert.assertEquals("B", tag.get(stable.get(1)));
        Assert.assertEquals("D", tag.get(stable.get(2)));
        Assert.assertEquals("C", tag.get(stable.get(3)));
    }
}
