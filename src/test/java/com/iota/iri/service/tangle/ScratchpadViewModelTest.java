package com.iota.iri.service.tangle;

import com.iota.iri.conf.Configuration;
import com.iota.iri.service.tangle.rocksDB.RocksDBPersistenceProvider;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.util.Random;

/**
 * Created by paul on 3/13/17 for iri-testnet.
 */
public class ScratchpadViewModelTest {
    static TemporaryFolder dbFolder = new TemporaryFolder();
    static TemporaryFolder logFolder = new TemporaryFolder();
    static Random seed;

    @BeforeClass
    public static void setUpClass() throws Exception {
        seed = new Random();
        dbFolder.create();
        logFolder.create();
        Configuration.put(Configuration.DefaultConfSettings.DB_PATH, dbFolder.getRoot().getAbsolutePath());
        Configuration.put(Configuration.DefaultConfSettings.DB_LOG_PATH, logFolder.getRoot().getAbsolutePath());
        Tangle.instance().addPersistenceProvider(new RocksDBPersistenceProvider());
        Tangle.instance().init();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        Tangle.instance().shutdown();
        dbFolder.delete();
    }

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void requestTransaction() throws Exception {

    }

    @Test
    public void setAnalyzedTransactionFlag() throws Exception {

    }

    @Test
    public void clearAnalyzedTransactionsFlags() throws Exception {

    }

    @Test
    public void getNumberOfTransactionsToRequest() throws Exception {

    }

    @Test
    public void clearReceivedTransaction() throws Exception {

    }

    @Test
    public void transactionToRequest() throws Exception {
    }

}