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

    //private static Neo4jPersistenceProvider rocksDBPersistenceProvider;
    private static Neo4jPersistenceProvider neo4jPersistenceProvider;
    //private static String dbPath = "tmpdb", dbLogPath = "tmplogs";

    @BeforeClass
    public static void setUpDb() {
        neo4jPersistenceProvider = new Neo4jPersistenceProvider("bolt://localhost:7687", "neo4j", "nlnt4ert");
        try {
            neo4jPersistenceProvider.init();
        } catch(Exception e) {}
    }

    @AfterClass
    public static void destroyDb() {
    }

    @Before
    public void setUp() throws Exception {
        //rocksDBPersistenceProvider.clear(Transaction.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testHello() throws Exception {
        TransactionViewModel transaction;
        transaction = new TransactionViewModel(getRandomTransactionTrits(), getRandomTransactionHash());
        neo4jPersistenceProvider.saveBatch(transaction.getSaveBatch());
    }
}