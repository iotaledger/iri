package com.iota.iri.storage.rocksDB;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;

import com.iota.iri.storage.Indexable;
import org.junit.*;

import java.util.LinkedList;
import java.util.List;

import static com.iota.iri.TransactionTestUtils.getTransactionHash;
import static com.iota.iri.TransactionTestUtils.getTransactionTrits;
import static com.iota.iri.TransactionTestUtils.getTransactionTritsWithTrunkAndBranch;

public class PermaRocksDBPersentenceProviderTest {
    private static RocksDBPPPImpl rocksDBPersistenceProvider;
    private static String dbPath = "tmpdb", dbLogPath = "tmplogs";
    //private static String dbPath = "mainnetpermadb", dbLogPath = "mainnetpermadb.log";

    @BeforeClass
    public static void setUpDb() throws Exception {
        rocksDBPersistenceProvider =  new RocksDBPPPImpl(
                dbPath, dbLogPath,1000);
        rocksDBPersistenceProvider.init();
    }

    @AfterClass
    public static void destroyDb() {
        rocksDBPersistenceProvider.shutdown();
        //FileUtils.deleteQuietly(new File(dbPath));
        //FileUtils.deleteQuietly(new File(dbLogPath));
        //rocksDBPersistenceProvider = null;
    }

    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void readData() throws Exception {
        rocksDBPersistenceProvider.loopAddressIndex();


    }


    @Test
    public void testSelectiveTransactionModel() throws Exception {

        TransactionViewModel transaction, transaction1, transaction2, transaction3, transaction4;
        transaction = new TransactionViewModel(getTransactionTrits(), Hash.NULL_HASH);
        transaction1 = new TransactionViewModel(getTransactionTritsWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getTransactionHash());
        transaction2 = new TransactionViewModel(getTransactionTritsWithTrunkAndBranch(transaction1.getHash(),
                transaction1.getHash()), getTransactionHash());
        transaction3 = new TransactionViewModel(getTransactionTritsWithTrunkAndBranch(transaction2.getHash(),
                transaction1.getHash()), getTransactionHash());
        transaction4 = new TransactionViewModel(getTransactionTritsWithTrunkAndBranch(transaction2.getHash(),
                transaction3.getHash()), getTransactionHash());

        List<TransactionViewModel> mm = new LinkedList<>();
        mm.add(transaction);
        mm.add(transaction1);
        mm.add(transaction2);
        mm.add(transaction3);
        mm.add(transaction4);

        for(TransactionViewModel v: mm){
            System.out.println(v.getHash() + " \t " + v.getAddressHash());
            rocksDBPersistenceProvider.saveTransaction(v, v.getHash());
        }



        rocksDBPersistenceProvider.incrementTransactions(new Indexable[]{transaction.getHash()});
        long count = rocksDBPersistenceProvider.getCounter(transaction.getHash());
        Assert.assertEquals(count, 2);

        //See if all indexes are updated.
        Assert.assertTrue(rocksDBPersistenceProvider.findAddress(transaction2.getAddressHash()).set.contains(transaction2.getHash()));
        Assert.assertTrue(rocksDBPersistenceProvider.findTag(transaction2.getTagValue()).set.contains(transaction2.getHash()));
        Assert.assertTrue(rocksDBPersistenceProvider.findBundle(transaction2.getBundleHash()).set.contains(transaction2.getHash()));
        Assert.assertTrue(rocksDBPersistenceProvider.findApprovee(transaction2.getTrunkTransactionHash()).set.contains(transaction2.getHash()));
        Assert.assertTrue(rocksDBPersistenceProvider.findApprovee(transaction2.getBranchTransactionHash()).set.contains(transaction2.getHash()));

        //Test decremeant delete;
        rocksDBPersistenceProvider.decrementTransactions(new Indexable[]{transaction3.getHash()});
        Assert.assertNull(rocksDBPersistenceProvider.getTransaction(transaction3.getHash()));

    }
}
