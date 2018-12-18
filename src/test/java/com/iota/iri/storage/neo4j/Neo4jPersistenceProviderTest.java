package com.iota.iri.storage.neo4j;

import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Pair;
import org.apache.commons.io.FileUtils;
import org.junit.*;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.iota.iri.controllers.TransactionViewModel;
import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionHash;
import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionTrits;
import static com.iota.iri.controllers.TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class Neo4jPersistenceProviderTest {

    private static Neo4jPersistenceProvider neo4jPersistenceProvider;
    private static String dbPath = "tmpdb";

    @BeforeClass
    public static void setUpDb() {
        neo4jPersistenceProvider = new Neo4jPersistenceProvider(dbPath);
        try {
            neo4jPersistenceProvider.init();
        } catch(Exception e) {}
    }

    @AfterClass
    public static void destroyDb() {
        neo4jPersistenceProvider.shutdown();
        FileUtils.deleteQuietly(new File(dbPath));
        neo4jPersistenceProvider = null;
    }

    @Before
    public void setUp() throws Exception {
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testHello() throws Exception {
        TransactionViewModel transaction, transaction1, transaction2, transaction3, transaction4, transaction5;
        transaction = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        transaction1 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction2 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction1.getHash(),
                transaction.getHash()), getRandomTransactionHash());
        transaction3 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction2.getHash(),
                transaction1.getHash()), getRandomTransactionHash());
        transaction4 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction1.getHash(),
                transaction3.getHash()), getRandomTransactionHash());
        transaction5 = new TransactionViewModel(getRandomTransactionWithTrunkAndBranch(transaction4.getHash(),
                transaction2.getHash()), getRandomTransactionHash());

        neo4jPersistenceProvider.saveBatch(transaction.getSaveBatch());
        neo4jPersistenceProvider.saveBatch(transaction1.getSaveBatch());
        neo4jPersistenceProvider.saveBatch(transaction2.getSaveBatch());
        neo4jPersistenceProvider.saveBatch(transaction3.getSaveBatch());
        neo4jPersistenceProvider.saveBatch(transaction4.getSaveBatch());
        neo4jPersistenceProvider.saveBatch(transaction5.getSaveBatch());
        Assert.assertEquals(6, neo4jPersistenceProvider.getTotalTxns());
    }
}
