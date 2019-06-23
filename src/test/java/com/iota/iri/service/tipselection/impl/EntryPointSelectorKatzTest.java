package com.iota.iri.service.tipselection.impl;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashId;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.localinmemorygraph.LocalInMemoryGraphProvider;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.collections.interfaces.UnIterableMap;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.model.persistables.Bundle;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import com.iota.iri.utils.Pair;
import java.io.PrintStream;

import static com.iota.iri.controllers.TransactionViewModelTest.*;

import java.util.HashMap;
import java.util.Map;

public class EntryPointSelectorKatzTest {
    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static final TemporaryFolder dbFolder1 = new TemporaryFolder();
    private static final TemporaryFolder logFolder1 = new TemporaryFolder();
    private static final String TX_CUMULATIVE_WEIGHT_IS_NOT_AS_EXPECTED_FORMAT =
        "tx%d cumulative weight is not as expected";
    private static Tangle tangle1;
    private static Tangle tangle2;
    private static EntryPointSelectorKatz selector1;
    private static EntryPointSelectorKatz selector2;
    private static LocalInMemoryGraphProvider provider1;
    private static LocalInMemoryGraphProvider provider2;

    @AfterClass
    public static void tearDown() throws Exception {
        tangle1.shutdown();
        tangle2.shutdown();
        dbFolder.delete();
        BaseIotaConfig.getInstance().setStreamingGraphSupport(false);
    }

    @BeforeClass
    public static void setUp() throws Exception {
        BaseIotaConfig.getInstance().setStreamingGraphSupport(true);
        tangle1 = new Tangle();
        tangle2 = new Tangle();
        dbFolder.create();
        logFolder.create();
        dbFolder1.create();
        logFolder1.create();
        tangle1.addPersistenceProvider(new RocksDBPersistenceProvider(dbFolder.getRoot().getAbsolutePath(), logFolder
                    .getRoot().getAbsolutePath(), 1000, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));
        provider1 = new LocalInMemoryGraphProvider("", tangle1);
        tangle1.addPersistenceProvider(provider1);
        tangle1.init();
        tangle2.addPersistenceProvider(new RocksDBPersistenceProvider(dbFolder1.getRoot().getAbsolutePath(), logFolder1
                    .getRoot().getAbsolutePath(), 1000, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));
        provider2 = new LocalInMemoryGraphProvider("", tangle2);
        tangle2.addPersistenceProvider(provider2);
        tangle2.init();
        BaseIotaConfig.getInstance().setConfluxScoreAlgo("KATZ");
    }

    @Test
    public void testGetEntryPointKatzOne() throws Exception {
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
        provider1.setNameMap(tag);

        A.store(tangle1);
        B.store(tangle1);
        D.store(tangle1);
        C.store(tangle1);
        E.store(tangle1);
        F.store(tangle1);
        G.store(tangle1);
        H.store(tangle1);

        // Compute without streaming graph
        BaseIotaConfig.getInstance().setStreamingGraphSupport(false);
        selector1 = new EntryPointSelectorKatz(tangle1, tag);
        Hash ret = selector1.getEntryPoint(-1);
        Assert.assertEquals(tag.get(A.getHash()),tag.get(ret));
        ret = selector1.getEntryPoint(2);
        Assert.assertEquals(tag.get(B.getHash()),tag.get(ret));
        ret = selector1.getEntryPoint(3);
        Assert.assertEquals(tag.get(A.getHash()),tag.get(ret));

        // Compute with streaming graph
        BaseIotaConfig.getInstance().setStreamingGraphSupport(true);
        ret = selector1.getEntryPoint(-1);
        Assert.assertEquals(tag.get(A.getHash()),tag.get(ret));
        ret = selector1.getEntryPoint(2);
        Assert.assertEquals(tag.get(B.getHash()),tag.get(ret));
        ret = selector1.getEntryPoint(3);
        Assert.assertEquals(tag.get(A.getHash()),tag.get(ret));

        // reset in memory graph
        provider1.close();
    }

    @Test
    public void testGetEntryPointKatzTwo() throws Exception {
//        LocalInMemoryGraphProvider.topOrderStreaming = new HashMap<>();
        BaseIotaConfig.getInstance().setStreamingGraphSupport(true);
        TransactionViewModel A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z, One, Two;
        A = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        B = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(A.getHash(),
                    A.getHash()), getRandomTransactionHash());
        C = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(A.getHash(),
                    A.getHash()), getRandomTransactionHash());
        D = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(A.getHash(),
                    A.getHash()), getRandomTransactionHash());
        E = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(A.getHash(),
                    A.getHash()), getRandomTransactionHash());
        F = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(D.getHash(),
                    E.getHash()), getRandomTransactionHash());
        G = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(B.getHash(),
                    C.getHash()), getRandomTransactionHash());
        H = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(D.getHash(),
                    E.getHash()), getRandomTransactionHash());
        I = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(G.getHash(),
                    F.getHash()), getRandomTransactionHash());
        J = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(G.getHash(),
                    F.getHash()), getRandomTransactionHash());
        K = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(J.getHash(),
                    H.getHash()), getRandomTransactionHash());
        L = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(I.getHash(),
                    H.getHash()), getRandomTransactionHash());
        M = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(I.getHash(),
                    J.getHash()), getRandomTransactionHash());
        N = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(K.getHash(),
                    M.getHash()), getRandomTransactionHash());
        O = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(I.getHash(),
                    K.getHash()), getRandomTransactionHash());
        P = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(M.getHash(),
                    K.getHash()), getRandomTransactionHash());
        Q = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(M.getHash(),
                    K.getHash()), getRandomTransactionHash());
        R = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(I.getHash(),
                    K.getHash()), getRandomTransactionHash());
        S = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(M.getHash(),
                    K.getHash()), getRandomTransactionHash());
        T = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(N.getHash(),
                    O.getHash()), getRandomTransactionHash());
        U = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(P.getHash(),
                    N.getHash()), getRandomTransactionHash());
        V = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(N.getHash(),
                    O.getHash()), getRandomTransactionHash());
        W = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(Q.getHash(),
                    S.getHash()), getRandomTransactionHash());
        X = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(S.getHash(),
                    U.getHash()), getRandomTransactionHash());
        Y = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(T.getHash(),
                    R.getHash()), getRandomTransactionHash());
        Z = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(V.getHash(),
                    T.getHash()), getRandomTransactionHash());
        One = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(W.getHash(),
                    X.getHash()), getRandomTransactionHash());
        Two = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(Z.getHash(),
                    Y.getHash()), getRandomTransactionHash());

        A.store(tangle2);
        B.store(tangle2);
        C.store(tangle2);
        D.store(tangle2);
        E.store(tangle2);
        F.store(tangle2);
        G.store(tangle2);
        H.store(tangle2);
        I.store(tangle2);
        J.store(tangle2);
        K.store(tangle2);
        L.store(tangle2);
        M.store(tangle2);
        N.store(tangle2);
        O.store(tangle2);
        P.store(tangle2);
        Q.store(tangle2);
        R.store(tangle2);
        S.store(tangle2);
        T.store(tangle2);
        U.store(tangle2);
        V.store(tangle2);
        W.store(tangle2);
        X.store(tangle2);
        Y.store(tangle2);
        Z.store(tangle2);

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
        tag.put(Q.getHash(), "Q");
        tag.put(R.getHash(), "R");
        tag.put(S.getHash(), "S");
        tag.put(T.getHash(), "T");
        tag.put(U.getHash(), "U");
        tag.put(V.getHash(), "V");
        tag.put(W.getHash(), "W");
        tag.put(X.getHash(), "X");
        tag.put(Y.getHash(), "Y");
        tag.put(Z.getHash(), "Z");

        // Compute without streaming graph
        BaseIotaConfig.getInstance().setStreamingGraphSupport(false);
        selector2 = new EntryPointSelectorKatz(tangle2, tag);
        Hash ret = selector2.getEntryPoint(-1);
        Assert.assertEquals(tag.get(A.getHash()),tag.get(ret));
        ret = selector2.getEntryPoint(3);
        Assert.assertEquals(tag.get(I.getHash()),tag.get(ret));
        ret = selector2.getEntryPoint(4);
        Assert.assertEquals(tag.get(G.getHash()),tag.get(ret));

        // Compute with streaming graph
        provider2.setNameMap(tag);
        BaseIotaConfig.getInstance().setStreamingGraphSupport(true);
        ret = selector2.getEntryPoint(-1);
        Assert.assertEquals(tag.get(A.getHash()),tag.get(ret));
        ret = selector2.getEntryPoint(3);
        Assert.assertEquals(tag.get(I.getHash()),tag.get(ret));
        ret = selector2.getEntryPoint(4);
        Assert.assertEquals(tag.get(G.getHash()),tag.get(ret));

        // reset in memory graph
        provider2.close();
    }
}
