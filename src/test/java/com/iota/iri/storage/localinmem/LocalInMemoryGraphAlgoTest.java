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
        BaseIotaConfig.getInstance().setConfluxScoreAlgo("CUM_WEIGHT");
    }

    @Test
    public void testInduceSubGraph() throws Exception {
        TransactionViewModel a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p;
        a = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        b = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                a.getHash()), getRandomTransactionHash());
        d = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(b.getHash(),
                b.getHash()), getRandomTransactionHash());
        c = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(d.getHash(),
                b.getHash()), getRandomTransactionHash());
        e = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(d.getHash(),
                b.getHash()), getRandomTransactionHash());
        f = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(e.getHash(),
                b.getHash()), getRandomTransactionHash());
        g = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(d.getHash(),
                c.getHash()), getRandomTransactionHash());
        h = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(e.getHash(),
                f.getHash()), getRandomTransactionHash());
        i = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(g.getHash(),
                h.getHash()), getRandomTransactionHash());
        j = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(c.getHash(),
                g.getHash()), getRandomTransactionHash());
        k = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(g.getHash(),
                j.getHash()), getRandomTransactionHash());
        l = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(k.getHash(),
                j.getHash()), getRandomTransactionHash());
        m = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(i.getHash(),
                h.getHash()), getRandomTransactionHash());
        n = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(m.getHash(),
                i.getHash()), getRandomTransactionHash());
        o = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(a.getHash(),
                b.getHash()), getRandomTransactionHash());
        p = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(o.getHash(),
                c.getHash()), getRandomTransactionHash());

        HashMap<Hash, String> tag = new HashMap<Hash, String>();

        tag.put(a.getHash(), "A");
        tag.put(b.getHash(), "B");
        tag.put(c.getHash(), "C");
        tag.put(d.getHash(), "D");
        tag.put(e.getHash(), "E");
        tag.put(f.getHash(), "F");
        tag.put(g.getHash(), "G");
        tag.put(h.getHash(), "H");
        tag.put(i.getHash(), "I");
        tag.put(j.getHash(), "J");
        tag.put(k.getHash(), "K");
        tag.put(l.getHash(), "L");
        tag.put(m.getHash(), "M");
        tag.put(n.getHash(), "N");
        tag.put(o.getHash(), "O");
        tag.put(p.getHash(), "P");

        provider1.setNameMap(tag);

        a.store(tangle1);
        b.store(tangle1);
        c.store(tangle1);
        d.store(tangle1);
        e.store(tangle1);
        f.store(tangle1);
        g.store(tangle1);
        h.store(tangle1);
        i.store(tangle1);
        j.store(tangle1);
        k.store(tangle1);
        l.store(tangle1);
        m.store(tangle1);
        n.store(tangle1);
        o.store(tangle1);
        p.store(tangle1);

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

        provider1.buildTempGraphs(order, g.getHash());
        Assert.assertEquals(provider1.subGraph.size(), 7);

        Assert.assertEquals(2, provider1.subGraph.get(l.getHash()).size());
        Assert.assertEquals(2, provider1.subGraph.get(k.getHash()).size());
        Assert.assertEquals(1, provider1.subGraph.get(j.getHash()).size());
        Assert.assertEquals(2, provider1.subGraph.get(n.getHash()).size());
        Assert.assertEquals(1, provider1.subGraph.get(m.getHash()).size());
        Assert.assertEquals(1, provider1.subGraph.get(i.getHash()).size());
        Assert.assertEquals(2, provider1.subGraph.get(g.getHash()).size());

        Assert.assertEquals(null, provider1.subRevGraph.get(l.getHash()));
        Assert.assertEquals(1, provider1.subRevGraph.get(k.getHash()).size());
        Assert.assertEquals(2, provider1.subRevGraph.get(j.getHash()).size());
        Assert.assertEquals(null, provider1.subRevGraph.get(n.getHash()));
        Assert.assertEquals(1, provider1.subRevGraph.get(m.getHash()).size());
        Assert.assertEquals(2, provider1.subRevGraph.get(i.getHash()).size());
        Assert.assertEquals(3, provider1.subRevGraph.get(g.getHash()).size());
        Assert.assertEquals(1, provider1.subRevGraph.get(c.getHash()).size());
        Assert.assertEquals(1, provider1.subRevGraph.get(d.getHash()).size());

        
        Assert.assertEquals(k.getHash(), provider1.subParentGraph.get(l.getHash()));
        Assert.assertEquals(g.getHash(), provider1.subParentGraph.get(k.getHash()));
        Assert.assertEquals(m.getHash(), provider1.subParentGraph.get(n.getHash()));
        Assert.assertEquals(i.getHash(), provider1.subParentGraph.get(m.getHash()));
        Assert.assertEquals(g.getHash(), provider1.subParentGraph.get(i.getHash()));
        Assert.assertEquals(d.getHash(), provider1.subParentGraph.get(g.getHash()));

        
        Assert.assertEquals(2, provider1.subParentRevGraph.get(g.getHash()).size());
        Assert.assertEquals(1, provider1.subParentRevGraph.get(k.getHash()).size());
        Assert.assertEquals(1, provider1.subParentRevGraph.get(i.getHash()).size());
        Assert.assertEquals(1, provider1.subParentRevGraph.get(m.getHash()).size());
        Assert.assertEquals(null, provider1.subParentRevGraph.get(n.getHash()));
        Assert.assertEquals(null, provider1.subParentRevGraph.get(l.getHash()));
        Assert.assertEquals(null, provider1.subParentRevGraph.get(j.getHash()));

        provider1.reserveTempGraphs(order, g.getHash());
 
        Stack<Hash> ancestors = new Stack<>();
        ancestors.add(g.getHash());
        provider1.storeAncestors(ancestors);

        provider1.shiftTempGraphs();
       
        //System.out.println(tag.get(provider1.getGenesis()));
        Assert.assertEquals(7.0, provider1.getScore(g.getHash()), 0.001);
        Assert.assertEquals(2.0, provider1.getScore(k.getHash()), 0.001);
        Assert.assertEquals(3.0, provider1.getScore(j.getHash()), 0.001);
        Assert.assertEquals(1.0, provider1.getScore(l.getHash()), 0.001);
        Assert.assertEquals(2.0, provider1.getScore(m.getHash()), 0.001);
        Assert.assertEquals(1.0, provider1.getScore(n.getHash()), 0.001);
        Assert.assertEquals(3.0, provider1.getScore(i.getHash()), 0.001);

        Assert.assertEquals(6.0, provider1.getParentScore(g.getHash()), 0.001);
        Assert.assertEquals(2.0, provider1.getParentScore(k.getHash()), 0.001);
        Assert.assertEquals(1.0, provider1.getParentScore(j.getHash()), 0.001);
        Assert.assertEquals(1.0, provider1.getParentScore(l.getHash()), 0.001);
        Assert.assertEquals(2.0, provider1.getParentScore(m.getHash()), 0.001);
        Assert.assertEquals(1.0, provider1.getParentScore(n.getHash()), 0.001);
        Assert.assertEquals(3.0, provider1.getParentScore(i.getHash()), 0.001);

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
