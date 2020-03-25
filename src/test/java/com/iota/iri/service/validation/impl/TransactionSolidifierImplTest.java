package com.iota.iri.service.validation.impl;

import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.impl.SnapshotMockUtils;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Converter;
import org.junit.Rule;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.AfterClass;
import org.junit.After;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static com.iota.iri.TransactionTestUtils.getTransactionTrits;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class TransactionSolidifierImplTest {
    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static Tangle tangle;

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private static SnapshotProvider snapshotProvider;

    @Mock
    private static TransactionSolidifierImpl txSolidifier;

    @Mock
    private static TipsViewModel tipsViewModel;

    @Mock
    private static TransactionRequester txRequester;

    @BeforeClass
    public static void setUp() throws Exception {
        dbFolder.create();
        logFolder.create();
        tangle = new Tangle();
        tangle.addPersistenceProvider(
                new RocksDBPersistenceProvider(
                        dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),1000, Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));
        tangle.init();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        tangle.shutdown();
        dbFolder.delete();
        logFolder.delete();
    }

    @Before
    public void setUpEach() {
        when(snapshotProvider.getInitialSnapshot()).thenReturn(SnapshotMockUtils.createSnapshot());
        txRequester = new TransactionRequester(tangle, snapshotProvider);
        txSolidifier = new TransactionSolidifierImpl(tangle, snapshotProvider, txRequester, tipsViewModel);
        txSolidifier.start();
    }

    @After
    public void tearDownEach(){
        txSolidifier.shutdown();
    }


    @Test
    public void verifyTxIsSolid() throws Exception {
        TransactionViewModel tx = getTxWithBranchAndTrunk();
        assertTrue("Expected transaction to be solid", txSolidifier.checkSolidity(tx.getHash()));
        assertTrue("Expected transaction to be solid", txSolidifier.checkSolidity(tx.getHash()));
    }

    @Test
    public void verifyTxIsNotSolid() throws Exception {
        TransactionViewModel tx = getTxWithoutBranchAndTrunk();
        assertFalse("Expected transaction to fail solidity check", txSolidifier.checkSolidity(tx.getHash()));
        assertFalse("Expected transaction to fail solidity check", txSolidifier.checkSolidity(tx.getHash()));
    }

    @Test
    public void getSolidificationQueue() throws Exception {
        TransactionViewModel mainTx = getTxWithBranchAndTrunk();
        for(int i = 0; i < 10; i++) {
            TransactionViewModel tx = getTxWithBranchAndTrunk();
            txSolidifier.addToSolidificationQueue(tx.getHash());
        }
        txSolidifier.addToSolidificationQueue(mainTx.getHash());
        assertTrue("Expected transaction to be present in the solidification queue",
                txSolidifier.getSolidificationQueue().contains(mainTx.getHash()));
    }

    @Test
    public void verifyTransactionIsProcessedFully() throws Exception {
        TransactionViewModel tx = getTxWithBranchAndTrunk();
        txSolidifier.addToSolidificationQueue(tx.getHash());

        //Time to process through the steps
        Thread.sleep(1000);
        assertTrue("Expected transaction to be present in the broadcast queue",
                txSolidifier.getBroadcastQueue().contains(tx));
    }


    @Test
    public void verifyInconsistentTransactionIsNotProcessedFully() throws Exception {
        TransactionViewModel tx = getTxWithoutBranchAndTrunk();
        txSolidifier.addToSolidificationQueue(tx.getHash());

        //Time to process through the steps
        Thread.sleep(1000);
        assertFalse("Expected transaction not to be present in the broadcast queue",
                txSolidifier.getBroadcastQueue().contains(tx));
    }

    private TransactionViewModel getTxWithBranchAndTrunk() throws Exception {
        TransactionViewModel tx, trunkTx, branchTx;
        String trytes = "999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999CFDEZBLZQYA9999999999999999999999999999999999999999999ZZWQHWD99C99999999C99999999CKWWDBWSCLMQULCTAAJGXDEMFJXPMGMAQIHDGHRBGEMUYNNCOK9YPHKEEFLFCZUSPMCJHAKLCIBQSGWAS999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999";

        byte[] trits = Converter.allocateTritsForTrytes(trytes.length());
        Converter.trits(trytes, trits, 0);
        trunkTx = new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));
        branchTx = new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));

        byte[] childTx = getTransactionTrits();
        System.arraycopy(trunkTx.getHash().trits(), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE);
        System.arraycopy(branchTx.getHash().trits(), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE);
        tx = new TransactionViewModel(childTx, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, childTx));

        trunkTx.store(tangle, snapshotProvider.getInitialSnapshot());
        branchTx.store(tangle, snapshotProvider.getInitialSnapshot());
        tx.store(tangle, snapshotProvider.getInitialSnapshot());

        return tx;
    }

    private TransactionViewModel getTxWithoutBranchAndTrunk() throws Exception {
        byte[] trits = getTransactionTrits();
        TransactionViewModel tx = new TransactionViewModel(trits, TransactionHash.calculate(SpongeFactory.Mode.CURLP81, trits));

        tx.store(tangle, snapshotProvider.getInitialSnapshot());

        return tx;
    }

}
