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
        Hash A = getRandomTransactionHash();
        Hash B = getRandomTransactionHash();
        Hash C = getRandomTransactionHash();
        Hash D = getRandomTransactionHash();
        Hash E = getRandomTransactionHash();
        Hash F = getRandomTransactionHash();
        Hash G = getRandomTransactionHash();
        Hash H = getRandomTransactionHash();

        HashMap<Hash, Set<Hash>> g = new HashMap<Hash, Set<Hash>>();
        g.put(A, new HashSet<Hash>());
        g.put(B, new HashSet<Hash>());
        g.put(C, new HashSet<Hash>());
        g.put(D, new HashSet<Hash>());
        g.put(E, new HashSet<Hash>());
        g.put(F, new HashSet<Hash>());
        g.put(G, new HashSet<Hash>());
        g.put(H, new HashSet<Hash>());
        g.get(B).add(A);
        g.get(C).add(B);
        g.get(C).add(D);
        g.get(D).add(B);
        g.get(E).add(B);
        g.get(E).add(D);
        g.get(F).add(B);
        g.get(F).add(E);
        g.get(G).add(C);
        g.get(G).add(D);
        g.get(H).add(E);
        g.get(H).add(F);


        HashMap<Hash, Set<Hash>> revG = new HashMap<Hash, Set<Hash>>();
        revG.put(A, new HashSet<Hash>());
        revG.put(B, new HashSet<Hash>());
        revG.put(C, new HashSet<Hash>());
        revG.put(D, new HashSet<Hash>());
        revG.put(E, new HashSet<Hash>());
        revG.put(F, new HashSet<Hash>());
        revG.put(G, new HashSet<Hash>());
        revG.put(H, new HashSet<Hash>());
        revG.get(A).add(B);
        revG.get(B).add(C);
        revG.get(B).add(D);
        revG.get(B).add(E);
        revG.get(B).add(F);
        revG.get(C).add(G);
        revG.get(D).add(C);
        revG.get(D).add(E);
        revG.get(D).add(G);
        revG.get(E).add(F);       
        revG.get(E).add(H);
        revG.get(F).add(H);

        HashMap<Hash, Double> score = CumWeightScore.compute(revG, g, A);

        Assert.assertEquals((Double)8.0, score.get(A));
        Assert.assertEquals((Double)7.0, score.get(B));
        Assert.assertEquals((Double)2.0, score.get(C));
        Assert.assertEquals((Double)6.0, score.get(D));
        Assert.assertEquals((Double)3.0, score.get(E));
        Assert.assertEquals((Double)2.0, score.get(F));
        Assert.assertEquals((Double)1.0, score.get(G));
        Assert.assertEquals((Double)1.0, score.get(H));
    }
}
