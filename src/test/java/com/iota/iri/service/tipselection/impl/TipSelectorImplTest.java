package com.iota.iri.service.tipselection.impl;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.conf.TipSelConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.service.ledger.LedgerService;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.tipselection.*;
import com.iota.iri.storage.Tangle;

import java.security.InvalidAlgorithmParameterException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.openjdk.jmh.annotations.TearDown;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TipSelectorImplTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private EntryPointSelector entryPointSelector;

    @Mock
    private RatingCalculator ratingCalculator;

    @Mock
    private Walker walker;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private Tangle tangle;

    @Mock
    private SnapshotProvider snapshotProvider;

    @Mock
    private TipSelConfig config;

    private static final Hash REFERENCE = HashFactory.TRANSACTION.create("ENTRYPOINT");

    private TipSelectorImpl tipSelector;

    public TipSelectorImplTest() {
        //Empty Constructor
    }

    @Before
    public void setUpEach() throws Exception {
        when(config.getAlpha()).thenReturn(BaseIotaConfig.Defaults.ALPHA);
        tipSelector = new TipSelectorImpl(tangle, snapshotProvider, ledgerService, entryPointSelector, ratingCalculator,
                walker, config);
    }

    @Test
    public void checkReferenceTest() throws Exception {
        Map<Hash, Integer> map = new HashMap<Hash, Integer>(){{put(REFERENCE, 0);}};
        tipSelector.checkReference(REFERENCE, map, null);
        //test passes if no exceptions are thrown
    }

    @Test(expected = InvalidAlgorithmParameterException.class)
    public void checkReferenceExceptionTest() throws Exception {
        tipSelector.checkReference(REFERENCE, Collections.emptyMap(), null);
        //test passes if exceptions is thrown
    }

    @Test
    public void checkReferenceAlpha0Test() throws Exception {
        WalkValidator walkValidator = mock(WalkValidator.class);
        when(config.getAlpha()).thenReturn(0d);
        when(walkValidator.isValid(REFERENCE)).thenReturn(true);
        tipSelector.checkReference(REFERENCE, null, walkValidator);
        //test passes if no exceptions are thrown
    }

    @Test(expected = InvalidAlgorithmParameterException.class)
    public void checkReferenceAlpha0ExceptionTest() throws Exception {
        WalkValidator walkValidator = mock(WalkValidator.class);
        when(config.getAlpha()).thenReturn(0d);
        when(walkValidator.isValid(REFERENCE)).thenReturn(false);
        tipSelector.checkReference(REFERENCE, null, walkValidator);
        //test passes if an exceptions is thrown
    }
}