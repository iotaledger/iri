package com.iota.iri.service.ledger.impl;

import com.iota.iri.BundleValidator;
import com.iota.iri.TangleMockUtils;
import com.iota.iri.TransactionTestUtils;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.ledger.LedgerException;
import com.iota.iri.service.milestone.MilestoneService;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.SnapshotService;
import com.iota.iri.service.spentaddresses.SpentAddressesException;
import com.iota.iri.service.spentaddresses.SpentAddressesProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;

import java.util.*;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.junit.JUnitRule;
import org.mockito.internal.util.collections.Sets;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRule;
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
    MilestoneService milestoneService;

    @Mock
    SpentAddressesProvider spentAddressesProvider;

    @Mock
    BundleValidator bundleValidator;


    public LedgerServiceImplTest() {

    }

    @Before
    public void setUp() throws Exception {
        ledgerService.init(tangle, snapshotProvider, snapshotService, milestoneService, spentAddressesProvider,
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
        verify(spentAddressesProvider, times(1)).saveAddressesBatch(
                eq(Arrays.asList(bundle.get(1).getAddressHash())));
    }
}