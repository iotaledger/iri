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


        tag.forEach((k, v) -> System.out.println("key:" + k + ",value:" + v));
        localInMemoryGraphProvider.pivotChain(A.getHash()).forEach(s -> System.out.println(s));

        // reset in memory graph
        LocalInMemoryGraphProvider provider = new LocalInMemoryGraphProvider("", tangle1);
        provider.close();
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
        localInMemoryGraphProvider.confluxOrder(B.getHash()).forEach(s -> System.out.println(s));

        // reset in memory graph
        LocalInMemoryGraphProvider provider = new LocalInMemoryGraphProvider("", tangle1);
        provider.close();
    }

    @Test
    public void testConfluxOrder() throws Exception {
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
//        System.out.println("============pivotChain============");
//        localInMemoryGraphProvider.pivotChain(H.getHash()).forEach(e -> System.out.println(e));
//        System.out.println("============parent============");
//        System.out.println(localInMemoryGraphProvider.getParent(H.getHash()));
//        localInMemoryGraphProvider.past(localInMemoryGraphProvider.graph,E.getHash()).forEach(s -> System.out.println(s));
        System.out.println("============confluxOrder============");
        localInMemoryGraphProvider.confluxOrder(H.getHash()).forEach(s -> System.out.println(s));

        // reset in memory graph
        LocalInMemoryGraphProvider provider = new LocalInMemoryGraphProvider("", tangle1);
        provider.close();
    }
}
