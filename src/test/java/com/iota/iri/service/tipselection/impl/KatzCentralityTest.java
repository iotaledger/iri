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

public class KatzCentralityTest {

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

        HashMap<Hash, Set<Hash>> g = new HashMap<Hash, Set<Hash>>();
        g.put(A, new HashSet<Hash>());
        g.put(B, new HashSet<Hash>());
        g.put(C, new HashSet<Hash>());
        g.put(D, new HashSet<Hash>());
        g.put(E, new HashSet<Hash>());
        g.put(F, new HashSet<Hash>());
        g.get(A).add(F);
        g.get(B).add(C);
        g.get(B).add(F);
        g.get(C).add(B);
        g.get(C).add(D);
        g.get(C).add(F);
        g.get(D).add(A);
        g.get(D).add(E);
        g.get(E).add(D);
        g.get(E).add(F);
        g.get(F).add(A);
        g.get(F).add(D);

        KatzCentrality  cent = new KatzCentrality(g, null, 0.5, 0.1);
        HashMap<Hash, Double> centrality = cent.compute();

        Assert.assertEquals(0.595, centrality.get(A), 0.001);
        Assert.assertEquals(0.040, centrality.get(B), 0.001);
        Assert.assertEquals(0.040, centrality.get(C), 0.001);
        Assert.assertEquals(0.503, centrality.get(D), 0.001);
        Assert.assertEquals(0.293, centrality.get(E), 0.001);
        Assert.assertEquals(0.550, centrality.get(F), 0.001);
    }
}
