package com.iota.iri.service.tipselection.impl;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.HashId;
import com.iota.iri.service.tipselection.RatingCalculator;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.collections.interfaces.UnIterableMap;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import com.iota.iri.model.Hash;
import java.util.*;

import static com.iota.iri.controllers.TransactionViewModelTest.*;

public class CumWeightScoreTest {

    @AfterClass
    public static void tearDown() throws Exception {
    }

    @BeforeClass
    public static void setUp() throws Exception {
    }

    @Test
    public void testComputeScore() throws Exception {
        Hash a = getRandomTransactionHash();
        Hash b = getRandomTransactionHash();
        Hash c = getRandomTransactionHash();
        Hash d = getRandomTransactionHash();
        Hash e = getRandomTransactionHash();
        Hash f = getRandomTransactionHash();
        Hash g = getRandomTransactionHash();
        Hash h = getRandomTransactionHash();

        HashMap<Hash, Set<Hash>> gr = new HashMap<Hash, Set<Hash>>();
        gr.put(a, new HashSet<Hash>());
        gr.put(b, new HashSet<Hash>());
        gr.put(c, new HashSet<Hash>());
        gr.put(d, new HashSet<Hash>());
        gr.put(e, new HashSet<Hash>());
        gr.put(f, new HashSet<Hash>());
        gr.put(g, new HashSet<Hash>());
        gr.put(h, new HashSet<Hash>());
        gr.get(b).add(a);
        gr.get(c).add(b);
        gr.get(c).add(d);
        gr.get(d).add(b);
        gr.get(e).add(b);
        gr.get(e).add(d);
        gr.get(f).add(b);
        gr.get(f).add(e);
        gr.get(g).add(c);
        gr.get(g).add(d);
        gr.get(h).add(e);
        gr.get(h).add(f);


        HashMap<Hash, Set<Hash>> revG = new HashMap<Hash, Set<Hash>>();
        revG.put(a, new HashSet<Hash>());
        revG.put(b, new HashSet<Hash>());
        revG.put(c, new HashSet<Hash>());
        revG.put(d, new HashSet<Hash>());
        revG.put(e, new HashSet<Hash>());
        revG.put(f, new HashSet<Hash>());
        revG.put(g, new HashSet<Hash>());
        revG.put(h, new HashSet<Hash>());
        revG.get(a).add(b);
        revG.get(b).add(c);
        revG.get(b).add(d);
        revG.get(b).add(e);
        revG.get(b).add(f);
        revG.get(c).add(g);
        revG.get(d).add(c);
        revG.get(d).add(e);
        revG.get(d).add(g);
        revG.get(e).add(f);       
        revG.get(e).add(h);
        revG.get(f).add(h);

        Map<Hash, Double> score = CumWeightScore.compute(revG, gr, a);

        Assert.assertEquals((Double)8.0, score.get(a));
        Assert.assertEquals((Double)7.0, score.get(b));
        Assert.assertEquals((Double)2.0, score.get(c));
        Assert.assertEquals((Double)6.0, score.get(d));
        Assert.assertEquals((Double)3.0, score.get(e));
        Assert.assertEquals((Double)2.0, score.get(f));
        Assert.assertEquals((Double)1.0, score.get(g));
        Assert.assertEquals((Double)1.0, score.get(h));

        Hash i = getRandomTransactionHash();
        gr.put(i, new HashSet<Hash>());
        gr.get(i).add(h);
        gr.get(i).add(g);
        revG.get(h).add(i);
        revG.get(g).add(i);

        score = CumWeightScore.update(gr, score, i, 1.0);

        Assert.assertEquals((Double)9.0, score.get(a));
        Assert.assertEquals((Double)8.0, score.get(b));
        Assert.assertEquals((Double)3.0, score.get(c));
        Assert.assertEquals((Double)7.0, score.get(d));
        Assert.assertEquals((Double)4.0, score.get(e));
        Assert.assertEquals((Double)3.0, score.get(f));
        Assert.assertEquals((Double)2.0, score.get(g));
        Assert.assertEquals((Double)2.0, score.get(h));
        Assert.assertEquals((Double)1.0, score.get(i));

        Hash j = getRandomTransactionHash();
        gr.put(j, new HashSet<Hash>());
        gr.get(j).add(g);
        gr.get(j).add(c);
        revG.get(g).add(j);
        revG.get(c).add(j);

        score = CumWeightScore.update(gr, score, j, 3.0);

        Assert.assertEquals((Double)12.0, score.get(a));
        Assert.assertEquals((Double)11.0, score.get(b));
        Assert.assertEquals((Double)6.0, score.get(c));
        Assert.assertEquals((Double)10.0, score.get(d));
        Assert.assertEquals((Double)4.0, score.get(e));
        Assert.assertEquals((Double)3.0, score.get(f));
        Assert.assertEquals((Double)5.0, score.get(g));
        Assert.assertEquals((Double)2.0, score.get(h));
        Assert.assertEquals((Double)1.0, score.get(i));
        Assert.assertEquals((Double)3.0, score.get(j));
    }

    @Test
    public void testComputeParentScore() throws Exception {
        Map<Hash, Hash> p = new HashMap<>();
        Map<Hash, Set<Hash>> rp = new HashMap<>();

        Hash none = getRandomTransactionHash();
        Hash a = getRandomTransactionHash();
        Hash b = getRandomTransactionHash();
        Hash c = getRandomTransactionHash();
        Hash d = getRandomTransactionHash();
        Hash e = getRandomTransactionHash();
        Hash f = getRandomTransactionHash();
        Hash g = getRandomTransactionHash();
        Hash h = getRandomTransactionHash();

        p.put(a, none);
        p.put(b, a);
        p.put(d, b);
        p.put(c, b);
        p.put(e, d);
        p.put(f, e);
        p.put(h, e);
        p.put(g, d);

        rp.put(a, new HashSet<>());
        rp.put(b, new HashSet<>());
        rp.put(c, new HashSet<>());
        rp.put(d, new HashSet<>());
        rp.put(e, new HashSet<>());
        rp.put(f, new HashSet<>());
        rp.put(g, new HashSet<>());
        rp.put(h, new HashSet<>());

        rp.get(a).add(b);
        rp.get(b).add(c);
        rp.get(b).add(d);
        rp.get(d).add(e);
        rp.get(d).add(g);
        rp.get(e).add(h);
        rp.get(e).add(f);

        Map<Hash, Double> score = CumWeightScore.computeParentScore(p, rp);

        Assert.assertEquals((Double)8.0, score.get(a));
        Assert.assertEquals((Double)7.0, score.get(b));
        Assert.assertEquals((Double)1.0, score.get(c));
        Assert.assertEquals((Double)5.0, score.get(d));
        Assert.assertEquals((Double)3.0, score.get(e));
        Assert.assertEquals((Double)1.0, score.get(f));
        Assert.assertEquals((Double)1.0, score.get(g));
        Assert.assertEquals((Double)1.0, score.get(h));

        Hash i = getRandomTransactionHash();
        p.put(i, g);
        rp.get(g).add(i);

        score = CumWeightScore.updateParentScore(p, score, i, 1.0);

        Assert.assertEquals((Double)9.0, score.get(a));
        Assert.assertEquals((Double)8.0, score.get(b));
        Assert.assertEquals((Double)1.0, score.get(c));
        Assert.assertEquals((Double)6.0, score.get(d));
        Assert.assertEquals((Double)3.0, score.get(e));
        Assert.assertEquals((Double)1.0, score.get(f));
        Assert.assertEquals((Double)2.0, score.get(g));
        Assert.assertEquals((Double)1.0, score.get(h));
        Assert.assertEquals((Double)1.0, score.get(i));

        Hash j = getRandomTransactionHash();
        p.put(j, g);
        rp.get(g).add(j);

        score = CumWeightScore.updateParentScore(p, score, j, 3.0);

        Assert.assertEquals((Double)12.0, score.get(a));
        Assert.assertEquals((Double)11.0, score.get(b));
        Assert.assertEquals((Double)1.0, score.get(c));
        Assert.assertEquals((Double)9.0, score.get(d));
        Assert.assertEquals((Double)3.0, score.get(e));
        Assert.assertEquals((Double)1.0, score.get(f));
        Assert.assertEquals((Double)5.0, score.get(g));
        Assert.assertEquals((Double)1.0, score.get(h));
        Assert.assertEquals((Double)1.0, score.get(i));
        Assert.assertEquals((Double)3.0, score.get(j));
    }
}