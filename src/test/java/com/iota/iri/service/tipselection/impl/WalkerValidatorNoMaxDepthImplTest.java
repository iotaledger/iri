package com.iota.iri.service.tipselection.impl;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.conf.TipSelConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.ledger.LedgerService;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.impl.SnapshotMockUtils;
import com.iota.iri.service.tipselection.WalkValidator;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.HashSet;

@RunWith(PowerMockRunner.class)
@PrepareForTest(TransactionViewModel.class)
public class WalkerValidatorNoMaxDepthImplTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();
    private static Tangle tangle;
    private TipSelConfig config = new MainnetConfig();

    @Mock
    private static SnapshotProvider snapshotProvider;

    @Mock
    private static LedgerService ledgerService;

    @BeforeClass
    public static void setUp() throws Exception {
        tangle = new Tangle();
        dbFolder.create();
        logFolder.create();
        tangle.addPersistenceProvider( new RocksDBPersistenceProvider(
                dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath(),1000,
                Tangle.COLUMN_FAMILIES, Tangle.METADATA_COLUMN_FAMILY));
        tangle.init();
    }

    @Before
    public void setUpEach() {
        Mockito.when(snapshotProvider.getLatestSnapshot()).thenReturn(SnapshotMockUtils.createSnapshot());
        Mockito.when(snapshotProvider.getInitialSnapshot()).thenReturn(SnapshotMockUtils.createSnapshot());
    }

    @Test
    public void shouldFailOnBelowMaxDepth() throws Exception {
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(0);
        tx.store(tangle, snapshotProvider.getInitialSnapshot());
        tx.setSnapshot(tangle, snapshotProvider.getInitialSnapshot(), 2);
        Hash hash = tx.getHash();
        tx.updateSolid(true);

        PowerMockito.mockStatic(TransactionViewModel.class);
        Mockito.when(TransactionViewModel.fromHash(tangle, hash)).thenReturn(tx);
        Mockito.when(ledgerService.isBalanceDiffConsistent(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(true);
        snapshotProvider.getLatestSnapshot().setIndex(Integer.MAX_VALUE);

        WalkValidator walkerValidator = new WalkValidatorImpl(tangle,snapshotProvider,ledgerService, config);
        Assert.assertFalse("Validation should have failed because transaction is below max depth", walkerValidator.isValid(hash));
    }

    @Test
    public void shouldPassWithoutCheckingMaxDepth() throws Exception{
        TransactionViewModel tx = TransactionTestUtils.createBundleHead(0);
        tx.store(tangle, snapshotProvider.getInitialSnapshot());
        tx.setSnapshot(tangle, snapshotProvider.getInitialSnapshot(), 2);
        Hash hash = tx.getHash();
        tx.updateSolid(true);

        PowerMockito.mockStatic(TransactionViewModel.class);
        Mockito.when(TransactionViewModel.fromHash(tangle, hash)).thenReturn(tx);
        Mockito.when(ledgerService.isBalanceDiffConsistent(new HashSet<>(), new HashMap<>(), hash))
                .thenReturn(true);
        snapshotProvider.getLatestSnapshot().setIndex(Integer.MAX_VALUE);

        WalkValidator walkerValidator = new WalkerValidatorNoMaxDepthImpl(tangle,snapshotProvider,ledgerService, config);
        Assert.assertTrue("Validation failed but should have succeeded without checking max depth", walkerValidator.isValid(hash));
    }
}