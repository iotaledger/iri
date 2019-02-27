package com.iota.iri.service.ledger.impl;

import com.iota.iri.BundleValidator;
import com.iota.iri.TangleMockUtils;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.service.milestone.MilestoneService;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.SnapshotService;
import com.iota.iri.service.spentaddresses.SpentAddressesService;
import com.iota.iri.storage.Tangle;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class LedgerServiceImplTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    private LedgerServiceImpl ledgerService = new LedgerServiceImpl();

    @Mock
    private Tangle tangle;

    @Mock(answer = Answers.RETURNS_MOCKS)
    private SnapshotProvider snapshotProvider;

    @Mock
    private SnapshotService snapshotService;

    @Mock
    private MilestoneService milestoneService;

    @Mock
    private SpentAddressesService spentAddressesService;

    @Mock
    private BundleValidator bundleValidator;


    public LedgerServiceImplTest() {

    }

    @Before
    public void setUp() {
        ledgerService.init(tangle, snapshotProvider, snapshotService, milestoneService, spentAddressesService,
                bundleValidator);

    }

    @Test
    public void generateBalanceDiff_persistsSpentAddresses() throws Exception {
        List<TransactionViewModel> bundle = TangleMockUtils.mockValidBundle(tangle, bundleValidator, 1,
                "A", "Z");
        TransactionViewModel tailTx = bundle.get(0);
        int milestoneIndex = 1;
        when(milestoneService.isTransactionConfirmed(tailTx, milestoneIndex)).thenReturn(false);
        when(snapshotProvider.getInitialSnapshot().getSolidEntryPoints()).thenReturn(Collections.emptyMap());
        ledgerService.generateBalanceDiff(new HashSet<>(), tailTx.getHash(), milestoneIndex);
        verify(spentAddressesService, times(1)).persistValidatedSpentAddressesAsync(eq(bundle));
    }
}