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
    public void testCalculate() throws Exception {
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

        HashMap<Hash, Double> score = CumWeightScore.compute(revG, gr, a);

        Assert.assertEquals((Double)8.0, score.get(a));
        Assert.assertEquals((Double)7.0, score.get(b));
        Assert.assertEquals((Double)2.0, score.get(c));
        Assert.assertEquals((Double)6.0, score.get(d));
        Assert.assertEquals((Double)3.0, score.get(e));
        Assert.assertEquals((Double)2.0, score.get(f));
        Assert.assertEquals((Double)1.0, score.get(g));
        Assert.assertEquals((Double)1.0, score.get(h));
    }
}
