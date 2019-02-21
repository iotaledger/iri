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
                .getRoot().getAbsolutePath(), 1000, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));
        tangle1.addPersistenceProvider(new LocalInMemoryGraphProvider("", tangle1));
        tangle1.init();
    }

    @Test
    public void testGetSibling() throws Exception {
        TransactionViewModel a, b, c, d, e, f, g, h;
        a = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        b = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                a.getHash()), getRandomTransactionHash());
        d = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                b.getHash()), getRandomTransactionHash());
        c = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                d.getHash()), getRandomTransactionHash());
        e = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                d.getHash()), getRandomTransactionHash());
        f = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                e.getHash()), getRandomTransactionHash());
        g = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(c.getHash(),
                d.getHash()), getRandomTransactionHash());
        h = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(e.getHash(),
                f.getHash()), getRandomTransactionHash());

        HashMap<Hash, String> tag = new HashMap<Hash, String>();
        tag.put(a.getHash(), "A");
        tag.put(b.getHash(), "B");
        tag.put(c.getHash(), "C");
        tag.put(d.getHash(), "D");
        tag.put(e.getHash(), "E");
        tag.put(f.getHash(), "F");
        tag.put(g.getHash(), "G");
        tag.put(h.getHash(), "H");
        LocalInMemoryGraphProvider.setNameMap(tag);

        a.store(tangle1);
        b.store(tangle1);
        d.store(tangle1);
        c.store(tangle1);
        e.store(tangle1);
        f.store(tangle1);
        g.store(tangle1);
        h.store(tangle1);

        LocalInMemoryGraphProvider localInMemoryGraphProvider = (LocalInMemoryGraphProvider) tangle1.getPersistenceProvider("LOCAL_GRAPH");
        Hash[] hashes = {c.getHash(), d.getHash(), f.getHash()};
        Assert.assertTrue(CollectionUtils.containsAll(localInMemoryGraphProvider.getSiblings(e.getHash()).stream().collect(Collectors.toList()), Arrays.asList(hashes)));
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

        System.out.println("=========pivot=======");
        assert localInMemoryGraphProvider.getPivot(a.getHash()).equals(end2.getHash()) || localInMemoryGraphProvider.getPivot(a.getHash()).equals(f.getHash()) ;
        // reset in memory graph
        localInMemoryGraphProvider.close();
    }

    @Test
    public void testPast() throws Exception {
        TransactionViewModel a, b, c, d, e, f, g, h;
        a = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        b = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                a.getHash()), getRandomTransactionHash());
        c = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                a.getHash()), getRandomTransactionHash());
        d = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                b.getHash()), getRandomTransactionHash());
        e = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                c.getHash()), getRandomTransactionHash());
        f = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(d.getHash(),
                e.getHash()), getRandomTransactionHash());
        g = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(e.getHash(),
                c.getHash()), getRandomTransactionHash());
        h = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(g.getHash(),
                f.getHash()), getRandomTransactionHash());

        HashMap<Hash, String> tag = new HashMap<Hash, String>();
        tag.put(a.getHash(), "A");
        tag.put(b.getHash(), "B");
        tag.put(c.getHash(), "C");
        tag.put(d.getHash(), "D");
        tag.put(e.getHash(), "E");
        tag.put(f.getHash(), "F");
        tag.put(g.getHash(), "G");
        tag.put(h.getHash(), "H");
        LocalInMemoryGraphProvider.setNameMap(tag);

        a.store(tangle1);
        b.store(tangle1);
        d.store(tangle1);
        c.store(tangle1);
        e.store(tangle1);
        f.store(tangle1);
        g.store(tangle1);
        h.store(tangle1);

        LocalInMemoryGraphProvider localInMemoryGraphProvider = (LocalInMemoryGraphProvider) tangle1.getPersistenceProvider("LOCAL_GRAPH");
        System.out.println("============past============");
        Set<Hash> assertingSet = Arrays.stream(new Hash[]{a.getHash(), b.getHash(), c.getHash(), e.getHash(), g.getHash()}).collect(Collectors.toSet());
        assert assertingSet.removeAll(localInMemoryGraphProvider.past(g.getHash())) && assertingSet.isEmpty();

        // reset in memory graph
        localInMemoryGraphProvider.close();
    }

    @Test
    public void testGetPivotChain() throws Exception {
        TransactionViewModel a, b, c, d, e, f, g, h;
        a = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        b = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                a.getHash()), getRandomTransactionHash());
        d = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                b.getHash()), getRandomTransactionHash());
        c = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                d.getHash()), getRandomTransactionHash());
        e = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                d.getHash()), getRandomTransactionHash());
        f = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                e.getHash()), getRandomTransactionHash());
        g = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(c.getHash(),
                d.getHash()), getRandomTransactionHash());
        h = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(e.getHash(),
                f.getHash()), getRandomTransactionHash());

        HashMap<Hash, String> tag = new HashMap<Hash, String>();
        tag.put(a.getHash(), "A");
        tag.put(b.getHash(), "B");
        tag.put(c.getHash(), "C");
        tag.put(d.getHash(), "D");
        tag.put(e.getHash(), "E");
        tag.put(f.getHash(), "F");
        tag.put(g.getHash(), "G");
        tag.put(h.getHash(), "H");
        LocalInMemoryGraphProvider.setNameMap(tag);

        a.store(tangle1);
        b.store(tangle1);
        d.store(tangle1);
        c.store(tangle1);
        e.store(tangle1);
        f.store(tangle1);
        g.store(tangle1);
        h.store(tangle1);

        LocalInMemoryGraphProvider localInMemoryGraphProvider = (LocalInMemoryGraphProvider) tangle1.getPersistenceProvider("LOCAL_GRAPH");
        System.out.println("=========testGetPivotChain=======");
        List<Hash> assertingList = Arrays.stream(new Hash[]{a.getHash(), b.getHash(), d.getHash()}).collect(Collectors.toList());
        List<Hash> rs = localInMemoryGraphProvider.pivotChain(a.getHash());
        assert assertingList.equals(rs);
        // reset in memory graph
        localInMemoryGraphProvider.close();
    }

    @Test
    public void testBuildSubGraph() throws Exception {
        TransactionViewModel a, b, c, d, e, f, g, h, i;
        a = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        i = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                a.getHash()), getRandomTransactionHash());
        b = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                a.getHash()), getRandomTransactionHash());
        d = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                i.getHash()), getRandomTransactionHash());
        c = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                d.getHash()), getRandomTransactionHash());
        e = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                d.getHash()), getRandomTransactionHash());
        f = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                e.getHash()), getRandomTransactionHash());
        g = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(c.getHash(),
                d.getHash()), getRandomTransactionHash());
        h = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(e.getHash(),
                f.getHash()), getRandomTransactionHash());

        HashMap<Hash, String> tag = new HashMap<Hash, String>();
        tag.put(a.getHash(), "A");
        tag.put(b.getHash(), "B");
        tag.put(c.getHash(), "C");
        tag.put(d.getHash(), "D");
        tag.put(e.getHash(), "E");
        tag.put(f.getHash(), "F");
        tag.put(g.getHash(), "G");
        tag.put(h.getHash(), "H");
        tag.put(i.getHash(), "M");
        LocalInMemoryGraphProvider.setNameMap(tag);

        a.store(tangle1);
        i.store(tangle1);
        b.store(tangle1);
        d.store(tangle1);
        c.store(tangle1);
        e.store(tangle1);
        f.store(tangle1);
        g.store(tangle1);
        h.store(tangle1);

        LocalInMemoryGraphProvider localInMemoryGraphProvider = (LocalInMemoryGraphProvider) tangle1.getPersistenceProvider("LOCAL_GRAPH");

        System.out.println("============buildSubGraph============");
        List<Hash> blocks = Arrays.stream(new Hash[]{b.getHash(), c.getHash(), d.getHash()}).collect(Collectors.toList());
        Map<Hash, Set<Hash>> subGraph = localInMemoryGraphProvider.buildSubGraph(blocks);
        Map<Hash,Set<Hash>> assertingMap = new HashMap(){{
            put(b.getHash(), Collections.emptySet());
            put(d.getHash(), Arrays.stream(new Hash[]{b.getHash()}).collect(Collectors.toSet()));
            put(c.getHash(), Arrays.stream(new Hash[]{b.getHash(), d.getHash()}).collect(Collectors.toSet()));
        }};
        assert  assertingMap.equals(subGraph);

        // reset in memory graph
        localInMemoryGraphProvider.close();
    }

    @Test
    public void testConfluxOrder() throws Exception {
        TransactionViewModel a, b, c, d, e, f, g, h, i;
        a = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        i = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                a.getHash()), getRandomTransactionHash());
        b = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                a.getHash()), getRandomTransactionHash());
        d = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                i.getHash()), getRandomTransactionHash());
        c = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                d.getHash()), getRandomTransactionHash());
        e = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                d.getHash()), getRandomTransactionHash());
        f = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                e.getHash()), getRandomTransactionHash());
        g = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(c.getHash(),
                d.getHash()), getRandomTransactionHash());
        h = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(e.getHash(),
                f.getHash()), getRandomTransactionHash());

        HashMap<Hash, String> tag = new HashMap<Hash, String>();
        tag.put(a.getHash(), "A");
        tag.put(b.getHash(), "B");
        tag.put(c.getHash(), "C");
        tag.put(d.getHash(), "D");
        tag.put(e.getHash(), "E");
        tag.put(f.getHash(), "F");
        tag.put(g.getHash(), "G");
        tag.put(h.getHash(), "H");
        tag.put(i.getHash(), "M");
        LocalInMemoryGraphProvider.setNameMap(tag);

        a.store(tangle1);
        i.store(tangle1);
        b.store(tangle1);
        d.store(tangle1);
        c.store(tangle1);
        e.store(tangle1);
        f.store(tangle1);
        g.store(tangle1);
        h.store(tangle1);

        LocalInMemoryGraphProvider localInMemoryGraphProvider = (LocalInMemoryGraphProvider) tangle1.getPersistenceProvider("LOCAL_GRAPH");

        System.out.println("============confluxOrder============");
        List<Hash> assertingList = Arrays.stream(new Hash[]{a.getHash(), b.getHash(), i.getHash(), d.getHash()}).collect(Collectors.toList());
        assert  assertingList.equals(localInMemoryGraphProvider.confluxOrder(d.getHash()));

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
        System.out.println("=============total order=======");
        List<Hash> assertingList1 = Arrays.stream(new Hash[]{a.getHash(),e.getHash(),d.getHash(),h.getHash(),b.getHash(),c.getHash(),f.getHash(),g.getHash(),j.getHash(),k.getHash(),i.getHash(),l.getHash(),o.getHash(),m.getHash(),n.getHash(),
                t.getHash(),r.getHash(),y.getHash(),v.getHash(),z.getHash(),end2.getHash()}).collect(Collectors.toList());
        List<Hash> assertingList2 = Arrays.stream(new Hash[]{a.getHash(),d.getHash(),e.getHash(),f.getHash()}).collect(Collectors.toList());

        List<Hash> rs = localInMemoryGraphProvider.totalTopOrder();
        // because the score is unfixed
        assert assertingList1.removeAll(rs) && assertingList1.isEmpty() || assertingList2.equals(rs);

        // reset in memory graph
        localInMemoryGraphProvider.close();
    }
}
