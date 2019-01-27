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

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

import static com.iota.iri.controllers.TransactionViewModelTest.*;

public class LocalInMemoryGraphProviderTest {
    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static final TemporaryFolder dbFolder1 = new TemporaryFolder();
    private static final TemporaryFolder logFolder1 = new TemporaryFolder();
    private static Tangle tangle1;

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
                .getRoot().getAbsolutePath(), 1000));
        tangle1.addPersistenceProvider(new LocalInMemoryGraphProvider("", tangle1));
        tangle1.init();
    }

    @Test
    public void testGetSibling() throws Exception {
        TransactionViewModel A, B, C, D, E, F, G, H;
        A = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        B = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(A.getHash(),
                A.getHash()), getRandomTransactionHash());
        D = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(B.getHash(),
                B.getHash()), getRandomTransactionHash());
        C = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(B.getHash(),
                D.getHash()), getRandomTransactionHash());
        E = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(B.getHash(),
                D.getHash()), getRandomTransactionHash());
        F = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(B.getHash(),
                E.getHash()), getRandomTransactionHash());
        G = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(C.getHash(),
                D.getHash()), getRandomTransactionHash());
        H = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(E.getHash(),
                F.getHash()), getRandomTransactionHash());

        HashMap<Hash, String> tag = new HashMap<Hash, String>();
        tag.put(A.getHash(), "A");
        tag.put(B.getHash(), "B");
        tag.put(C.getHash(), "C");
        tag.put(D.getHash(), "D");
        tag.put(E.getHash(), "E");
        tag.put(F.getHash(), "F");
        tag.put(G.getHash(), "G");
        tag.put(H.getHash(), "H");
        LocalInMemoryGraphProvider.setNameMap(tag);

        A.store(tangle1);
        B.store(tangle1);
        D.store(tangle1);
        C.store(tangle1);
        E.store(tangle1);
        F.store(tangle1);
        G.store(tangle1);
        H.store(tangle1);

        LocalInMemoryGraphProvider localInMemoryGraphProvider = (LocalInMemoryGraphProvider) tangle1.getPersistenceProvider("LOCAL_GRAPH");
        Hash[] hashes = {C.getHash(), D.getHash(), F.getHash()};
        Assert.assertTrue(CollectionUtils.containsAll(localInMemoryGraphProvider.getSiblings(E.getHash()).stream().collect(Collectors.toList()), Arrays.asList(hashes)));
        // reset in memory graph
        localInMemoryGraphProvider.close();
    }

    @Test
    public void testGetPivot() throws Exception {
        TransactionViewModel a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x, y, z, end1, end2;
        a = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        b = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                a.getHash()), getRandomTransactionHash());
        c = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                a.getHash()), getRandomTransactionHash());
        d = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                a.getHash()), getRandomTransactionHash());
        e = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                a.getHash()), getRandomTransactionHash());
        h = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(e.getHash(),
                d.getHash()), getRandomTransactionHash());
        f = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(d.getHash(),
                e.getHash()), getRandomTransactionHash());
        g = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                c.getHash()), getRandomTransactionHash());
        i = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(g.getHash(),
                f.getHash()), getRandomTransactionHash());
        j = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(g.getHash(),
                f.getHash()), getRandomTransactionHash());
        m = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(j.getHash(),
                i.getHash()), getRandomTransactionHash());
        k = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(h.getHash(),
                j.getHash()), getRandomTransactionHash());
        l = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(i.getHash(),
                h.getHash()), getRandomTransactionHash());
        q = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(m.getHash(),
                k.getHash()), getRandomTransactionHash());
        s = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(k.getHash(),
                m.getHash()), getRandomTransactionHash());
        p = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(m.getHash(),
                k.getHash()), getRandomTransactionHash());
        n = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(l.getHash(),
                l.getHash()), getRandomTransactionHash());
        o = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(k.getHash(),
                l.getHash()), getRandomTransactionHash());
        r = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(k.getHash(),
                l.getHash()), getRandomTransactionHash());
        u = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(p.getHash(),
                n.getHash()), getRandomTransactionHash());
        v = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(n.getHash(),
                o.getHash()), getRandomTransactionHash());
        t = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(o.getHash(),
                n.getHash()), getRandomTransactionHash());
        w = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(q.getHash(),
                s.getHash()), getRandomTransactionHash());
        x = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(s.getHash(),
                u.getHash()), getRandomTransactionHash());
        y = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(t.getHash(),
                r.getHash()), getRandomTransactionHash());
        z = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(v.getHash(),
                t.getHash()), getRandomTransactionHash());
        end1 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(x.getHash(),
                w.getHash()), getRandomTransactionHash());
        end2 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(y.getHash(),
                z.getHash()), getRandomTransactionHash());

        TransactionViewModel[] models = {a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x, y, z, end1, end2};
        char[] modelChar = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r'
                , 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '1', '2'};
        HashMap<Hash, String> tag = new HashMap<Hash, String>();
        for (int offset = 0; offset < models.length; offset++) {
            tag.put(models[offset].getHash(), String.valueOf(modelChar[offset]));
        }
        LocalInMemoryGraphProvider.setNameMap(tag);

        Arrays.stream(models).forEach(model -> {
            try {
                model.store(tangle1);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        });

        LocalInMemoryGraphProvider localInMemoryGraphProvider = (LocalInMemoryGraphProvider) tangle1.getPersistenceProvider("LOCAL_GRAPH");

        tag.forEach((key, value) -> System.out.println("key:" + k + ",value:" + v));
        System.out.println("=========pivot=======");
        System.out.println(tag.get(localInMemoryGraphProvider.getPivot(a.getHash())));
//        System.out.println(tag.get(localInMemoryGraphProvider.getPivot(e.getHash())));
//        System.out.println(tag.get(localInMemoryGraphProvider.getPivot(f.getHash())));
        System.out.println();
        // reset in memory graph
        localInMemoryGraphProvider.close();
    }

    @Test
    public void testPast() throws Exception {
        TransactionViewModel A, B, C, D, E, F, G, H;
        A = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        B = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(A.getHash(),
                A.getHash()), getRandomTransactionHash());
        D = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(B.getHash(),
                B.getHash()), getRandomTransactionHash());
        C = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(B.getHash(),
                D.getHash()), getRandomTransactionHash());
        E = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(B.getHash(),
                D.getHash()), getRandomTransactionHash());
        F = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(B.getHash(),
                E.getHash()), getRandomTransactionHash());
        G = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(C.getHash(),
                D.getHash()), getRandomTransactionHash());
        H = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(E.getHash(),
                F.getHash()), getRandomTransactionHash());

        HashMap<Hash, String> tag = new HashMap<Hash, String>();
        tag.put(A.getHash(), "A");
        tag.put(B.getHash(), "B");
        tag.put(C.getHash(), "C");
        tag.put(D.getHash(), "D");
        tag.put(E.getHash(), "E");
        tag.put(F.getHash(), "F");
        tag.put(G.getHash(), "G");
        tag.put(H.getHash(), "H");
        LocalInMemoryGraphProvider.setNameMap(tag);

        A.store(tangle1);
        B.store(tangle1);
        D.store(tangle1);
        C.store(tangle1);
        E.store(tangle1);
        F.store(tangle1);
        G.store(tangle1);
        H.store(tangle1);

        LocalInMemoryGraphProvider localInMemoryGraphProvider = (LocalInMemoryGraphProvider) tangle1.getPersistenceProvider("LOCAL_GRAPH");
        tag.forEach((k, v) -> System.out.println("key:" + k + ",value:" + v));

        localInMemoryGraphProvider.printGraph(localInMemoryGraphProvider.graph);
        System.out.println("============past============");
        localInMemoryGraphProvider.past(B.getHash()).forEach(s -> System.out.print(tag.get(s)));
        System.out.println();

        // reset in memory graph
        localInMemoryGraphProvider.close();
    }

    @Test
    public void testGetPivotChain() throws Exception {
        TransactionViewModel A, B, C, D, E, F, G, H;
        A = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        B = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(A.getHash(),
                A.getHash()), getRandomTransactionHash());
        D = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(B.getHash(),
                B.getHash()), getRandomTransactionHash());
        C = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(B.getHash(),
                D.getHash()), getRandomTransactionHash());
        E = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(B.getHash(),
                D.getHash()), getRandomTransactionHash());
        F = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(B.getHash(),
                E.getHash()), getRandomTransactionHash());
        G = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(C.getHash(),
                D.getHash()), getRandomTransactionHash());
        H = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(E.getHash(),
                F.getHash()), getRandomTransactionHash());

        HashMap<Hash, String> tag = new HashMap<Hash, String>();
        tag.put(A.getHash(), "A");
        tag.put(B.getHash(), "B");
        tag.put(C.getHash(), "C");
        tag.put(D.getHash(), "D");
        tag.put(E.getHash(), "E");
        tag.put(F.getHash(), "F");
        tag.put(G.getHash(), "G");
        tag.put(H.getHash(), "H");
        LocalInMemoryGraphProvider.setNameMap(tag);

        A.store(tangle1);
        B.store(tangle1);
        D.store(tangle1);
        C.store(tangle1);
        E.store(tangle1);
        F.store(tangle1);
        G.store(tangle1);
        H.store(tangle1);

        LocalInMemoryGraphProvider localInMemoryGraphProvider = (LocalInMemoryGraphProvider) tangle1.getPersistenceProvider("LOCAL_GRAPH");

        tag.forEach((k, v) -> System.out.println("key:" + k + ",value:" + v));
        System.out.println("=========pivot chain=======");
        localInMemoryGraphProvider.pivotChain(A.getHash()).forEach(s -> System.out.print(tag.get(s)));
        System.out.println();
        // reset in memory graph
        localInMemoryGraphProvider.close();
    }

    @Test
    public void testConfluxOrder() throws Exception {
        TransactionViewModel A, B, C, D, E, F, G, H, I;
        A = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        I = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(A.getHash(),
                A.getHash()), getRandomTransactionHash());
        B = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(A.getHash(),
                A.getHash()), getRandomTransactionHash());
        D = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(B.getHash(),
                I.getHash()), getRandomTransactionHash());
        C = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(B.getHash(),
                D.getHash()), getRandomTransactionHash());
        E = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(B.getHash(),
                D.getHash()), getRandomTransactionHash());
        F = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(B.getHash(),
                E.getHash()), getRandomTransactionHash());
        G = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(C.getHash(),
                D.getHash()), getRandomTransactionHash());
        H = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(E.getHash(),
                F.getHash()), getRandomTransactionHash());

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
        LocalInMemoryGraphProvider.setNameMap(tag);

        A.store(tangle1);
        I.store(tangle1);
        B.store(tangle1);
        D.store(tangle1);
        C.store(tangle1);
        E.store(tangle1);
        F.store(tangle1);
        G.store(tangle1);
        H.store(tangle1);

        LocalInMemoryGraphProvider localInMemoryGraphProvider = (LocalInMemoryGraphProvider) tangle1.getPersistenceProvider("LOCAL_GRAPH");

        tag.forEach((k, v) -> System.out.println("key:" + k + ",value:" + v));
        System.out.println("============confluxOrder============");
        localInMemoryGraphProvider.confluxOrder(D.getHash()).forEach(s -> System.out.print(tag.get(s)));
        System.out.println();

        // reset in memory graph
        localInMemoryGraphProvider.close();
    }

    @Test
    public void testTotalTopOrder() throws Exception {
        TransactionViewModel a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x, y, z, end1, end2;
        a = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        b = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                a.getHash()), getRandomTransactionHash());
        c = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                a.getHash()), getRandomTransactionHash());
        d = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                a.getHash()), getRandomTransactionHash());
        e = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                a.getHash()), getRandomTransactionHash());
        h = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(e.getHash(),
                d.getHash()), getRandomTransactionHash());
        f = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(d.getHash(),
                e.getHash()), getRandomTransactionHash());
        g = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                c.getHash()), getRandomTransactionHash());
        i = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(g.getHash(),
                f.getHash()), getRandomTransactionHash());
        j = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(g.getHash(),
                f.getHash()), getRandomTransactionHash());
        m = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(j.getHash(),
                i.getHash()), getRandomTransactionHash());
        k = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(h.getHash(),
                j.getHash()), getRandomTransactionHash());
        l = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(i.getHash(),
                h.getHash()), getRandomTransactionHash());
        q = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(m.getHash(),
                k.getHash()), getRandomTransactionHash());
        s = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(k.getHash(),
                m.getHash()), getRandomTransactionHash());
        p = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(m.getHash(),
                k.getHash()), getRandomTransactionHash());
        n = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(m.getHash(),
                k.getHash()), getRandomTransactionHash());
        o = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(k.getHash(),
                l.getHash()), getRandomTransactionHash());
        r = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(k.getHash(),
                l.getHash()), getRandomTransactionHash());
        u = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(p.getHash(),
                n.getHash()), getRandomTransactionHash());
        v = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(n.getHash(),
                o.getHash()), getRandomTransactionHash());
        t = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(o.getHash(),
                n.getHash()), getRandomTransactionHash());
        w = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(q.getHash(),
                s.getHash()), getRandomTransactionHash());
        x = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(s.getHash(),
                u.getHash()), getRandomTransactionHash());
        y = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(t.getHash(),
                r.getHash()), getRandomTransactionHash());
        z = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(v.getHash(),
                t.getHash()), getRandomTransactionHash());
        end1 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(x.getHash(),
                w.getHash()), getRandomTransactionHash());
        end2 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(y.getHash(),
                z.getHash()), getRandomTransactionHash());

        TransactionViewModel[] models = {a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x, y, z, end1, end2};
        char[] modelChar = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r'
                , 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '1', '2'};
        HashMap<Hash, String> tag = new HashMap<Hash, String>();
        for (int offset = 0; offset < models.length; offset++) {
            tag.put(models[offset].getHash(), String.valueOf(modelChar[offset]));
        }
        LocalInMemoryGraphProvider.setNameMap(tag);

        Arrays.stream(models).forEach(model -> {
            try {
                model.store(tangle1);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        });

        LocalInMemoryGraphProvider localInMemoryGraphProvider = (LocalInMemoryGraphProvider) tangle1.getPersistenceProvider("LOCAL_GRAPH");
        System.out.println("============pivot order============");
        localInMemoryGraphProvider.pivotChain(a.getHash()).forEach(block -> System.out.print(tag.get(block)));
        System.out.println();
        System.out.println("=============total order=======");
        localInMemoryGraphProvider.totalTopOrder().forEach(block -> System.out.print(tag.get(block)));
        System.out.println();

        // reset in memory graph
        localInMemoryGraphProvider.close();
    }
}
