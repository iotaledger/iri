package com.iota.iri.service.tangle;

import com.iota.iri.hash.Curl;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Scratchpad;
import com.iota.iri.service.ScratchpadViewModel;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.service.tangle.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.service.viewModels.TransactionViewModel;
import com.iota.iri.utils.Converter;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Created by paul on 3/13/17 for iri-testnet.
 */
public class ScratchpadViewModelTest {
    static TemporaryFolder dbFolder = new TemporaryFolder();
    static Random seed;

    @BeforeClass
    public static void setUpClass() throws Exception {
        seed = new Random();
        dbFolder.create();
        Tangle.instance().addPersistenceProvider(new RocksDBPersistenceProvider());
        Tangle.instance().init(dbFolder.getRoot().getAbsolutePath());
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
        byte[] hashRequest = new byte[TransactionViewModel.HASH_SIZE], newRequest;
        ScratchpadViewModel.instance().transactionToRequest(hashRequest, 0);
        assertArrayEquals("Hash should be null hash", hashRequest, TransactionViewModel.NULL_TRANSACTION_HASH_BYTES);

        newRequest = Arrays.copyOf(Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> seed.nextInt(3)-1).toArray()), Hash.SIZE_IN_BYTES);
        ScratchpadViewModel.instance().requestTransaction(newRequest);

        ScratchpadViewModel.instance().transactionToRequest(hashRequest, 0);
        assertArrayEquals("Hash should be null hash", hashRequest, newRequest);
    }

}