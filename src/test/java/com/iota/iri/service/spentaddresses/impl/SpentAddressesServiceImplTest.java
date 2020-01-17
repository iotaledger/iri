package com.iota.iri.service.spentaddresses.impl;

import com.iota.iri.BundleValidator;
import com.iota.iri.TangleMockUtils;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.spentaddresses.SpentAddressesProvider;
import com.iota.iri.storage.Tangle;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

public class SpentAddressesServiceImplTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Tangle tangle;

    @Mock
    private BundleValidator bundleValidator;

    @Mock
    private SnapshotProvider snapshotProvider;

    @Mock
    private SpentAddressesProvider spentAddressesProvider;

    @Test
    public void doesntPersistZeroValueBundles() throws Exception {
        SpentAddressesServiceImpl spentAddressesService = new SpentAddressesServiceImpl(tangle, snapshotProvider, spentAddressesProvider, bundleValidator, null);
        List<TransactionViewModel> bundle = TangleMockUtils.mockValidBundle(tangle, bundleValidator, 1);
        spentAddressesService.persistValidatedSpentAddressesAsync(bundle);
        verify(spentAddressesProvider, never()).saveAddressesBatch(any());
    }
}
