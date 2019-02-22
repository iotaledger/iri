package com.iota.iri.service.tipselection.impl;

import com.iota.iri.model.Hash;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.storage.Tangle;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.junit.rules.TemporaryFolder;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.TransactionTestUtils;
import com.iota.iri.controllers.TransactionViewModelTest;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Indexable;
import com.iota.iri.utils.Pair;
import com.iota.iri.service.tipselection.RatingCalculator;
import com.iota.iri.utils.collections.interfaces.UnIterableMap;
import com.iota.iri.model.HashId;
import com.iota.iri.zmq.MessageQ;
import com.iota.iri.conf.MainnetConfig;
import java.util.*;
import java.io.*;

@RunWith(MockitoJUnitRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DoubleSpendTest {

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static Tangle tangle;
    private static Map<Hash, String> names;
    private static WalkerAlpha walker;

    //@AfterClass
    public static void tearDown() throws Exception {
        tangle.shutdown();
        dbFolder.delete();
    }

    @BeforeClass
    public static void setUp() throws Exception {
        tangle = new Tangle();
        dbFolder.create();
        logFolder.create();
        tangle.addPersistenceProvider(new RocksDBPersistenceProvider(dbFolder.getRoot().getAbsolutePath(), logFolder
                .getRoot().getAbsolutePath(), 1000, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));
        tangle.init();
        names = new HashMap<Hash, String>();
        MessageQ messageQ = Mockito.mock(MessageQ.class);
        walker = new WalkerAlpha((Optional::of), tangle, messageQ, new Random(1), new MainnetConfig());
    }

    @Test
    public void testDoubleSpend() throws Exception {
        // ALICE has 5 iota
        Transaction t = new Transaction();
        TransactionViewModel gen = new TransactionViewModel(t, TransactionViewModelTest.getRandomTransactionHash());

        gen.store(tangle);
        names.put(gen.getHash(), "gen");

        RatingCalculator ratingCalculator = new CumulativeWeightWithEdgeCalculator(tangle);
        ((CumulativeWeightWithEdgeCalculator)ratingCalculator).setIsUseUnifiedEdgeWeight(true);
        // ALICE -> BOB
        Hash bobtail = createBundleTransaction("ALICE", "BOB", -5, 5, gen.getHash(), gen.getHash(), tangle);
        batchApprove(gen.getHash(), bobtail, 60, 5, "BOB_APP", walker, ratingCalculator);
        // ALICE -> JOE
        Hash joetail = createBundleTransaction("ALICE", "JOE", -5, 5, gen.getHash(), gen.getHash(), tangle);
        batchApprove(gen.getHash(), joetail, 40, 5, "JOE_APP", walker, ratingCalculator);

        // Now do the experiment
        UnIterableMap<HashId, Integer> rating = ratingCalculator.calculate(gen.getHash());
        printGraph(tangle,rating);

        int numBob=0, numJoe=0;
        for (int i=0; i < 100; i++) {
            Hash tip = walker.walk(gen.getHash(), rating, (o -> true));
            Assert.assertTrue(tip != null);
            if(names.get(tip).startsWith("BOB"))
                numBob++;
            if(names.get(tip).startsWith("JOE"))
                numJoe++;
        }
        System.out.println("BOB: "+numBob+" JOE: "+numJoe);
        Assert.assertTrue((numBob>numJoe) && (numBob-numJoe>30));
    }

    public Hash createBundleTransaction(String from,
                                        String to,
                                        long fromValue,
                                        long toValue,
                                        Hash branch,
                                        Hash trunk,
                                        Tangle tangle) throws Exception
    {
        TransactionViewModel a2 = TransactionTestUtils.createBundleHead(2, branch, trunk);
        TransactionViewModel a1 = TransactionTestUtils.createTransactionWithTrunkBundleHash(a2, branch);
        TransactionViewModel a0 = TransactionTestUtils.createTransactionWithTrunkBundleHash(a1, branch);

        a2.store(tangle);
        a1.store(tangle);
        a0.store(tangle);

        names.put(a2.getHash(), to+"2");
        names.put(a1.getHash(), to+"1");
        names.put(a0.getHash(), to+"0");
        return a0.getHash();
    }

    private void batchApprove(Hash gen,
                              Hash branch,
                              int numApprover,
                              long lambda,
                              String pfx,
                              WalkerAlpha walker,
                              RatingCalculator ratingCalculator)
    {
        Vector<TransactionViewModel> tmp = new Vector<>();
        try
        {
            Hash orig = branch;
            Hash trunk = gen;
            for(int i=0; i<numApprover; i++)
            {
                int count = 0;
                UnIterableMap<HashId, Integer> rating = ratingCalculator.calculate(gen);
                trunk = walker.walk(orig, rating, (o -> true));
                do
                {
                    branch = walker.walk(orig, rating, (o -> true));
                } while(count++<10 && branch.equals(trunk));

                TransactionViewModel txn = new TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(trunk,branch),
                                                                    TransactionViewModelTest.getRandomTransactionHash());
                tmp.add(txn);
                names.put(txn.getHash(), pfx+i);
                if(i!=0 && i % lambda == 0)
                {
                    for(TransactionViewModel view : tmp)
                    {
                        view.store(tangle);
                    }
                    tmp.clear();
                }
            }
            for(TransactionViewModel view : tmp)
            {
                view.store(tangle);
            }
            tmp.clear();
        }
        catch(Exception e)
        {
            e.printStackTrace(new PrintStream(System.out));
        }
    }

    class OneEdge
    {
        OneEdge(Hash to, long timeDiff)
        {
            this.to = to;
            this.timeDiff = timeDiff;
        }

        public Hash to;
        public long timeDiff;
    }

    // Get the graph using the BFS method
    private HashMap<Hash, Set<OneEdge>> buildGraph(Tangle tangle)
    {
        HashMap<Hash, Set<OneEdge>> ret = new HashMap<Hash, Set<OneEdge>>();
        try
        {
            Pair<Indexable, Persistable> one = tangle.getFirst(Transaction.class, TransactionHash.class);
            while(one != null)
            {
                TransactionViewModel model = new TransactionViewModel((Transaction)one.hi, (TransactionHash)one.low);
                Hash trunk = model.getTrunkTransactionHash();
                Hash branch = model.getBranchTransactionHash();
                TransactionViewModel trunkView = model.getTrunkTransaction(tangle);
                TransactionViewModel branchView = model.getBranchTransaction(tangle);
                OneEdge trunkEdge = new OneEdge(trunk, model.getArrivalTime()-trunkView.getArrivalTime());
                OneEdge branchEdge = new OneEdge(branch, model.getArrivalTime()-branchView.getArrivalTime());
                if(ret.get(model.getHash()) == null)
                {
                    ret.put(model.getHash(), new HashSet<OneEdge>());
                }
                ret.get(model.getHash()).add(trunkEdge);
                ret.get(model.getHash()).add(branchEdge);

                one = tangle.next(Transaction.class, one.low);
            }
        }
        catch(NullPointerException e)
        {
            ; // Do nothing
        }
        catch(Exception e)
        {
            e.printStackTrace(new PrintStream(System.out));
        }
        return ret;
    }

    // for graphviz visualization
    void printGraph(Tangle tangle, UnIterableMap<HashId, Integer> rMap)
    {
        HashMap<Hash, Set<OneEdge>> graph = buildGraph(tangle);
        for(Hash key : graph.keySet())
        {
            for(OneEdge val : graph.get(key))
            {
                System.out.println("\""+names.get(key)+":"+rMap.get(key)+"\"->"+
                                   "\""+names.get(val.to)+":"+rMap.get(val.to)+"\"");
            }
        }
    }
}
