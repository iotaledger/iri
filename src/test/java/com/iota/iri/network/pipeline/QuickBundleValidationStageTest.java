package com.iota.iri.network.pipeline;

import com.iota.iri.BundleValidator;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.validation.TransactionValidator;
import com.iota.iri.storage.Tangle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class QuickBundleValidationStageTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    BundleValidator bundleValidator;
    @Mock
    TransactionValidator transactionValidator;
    @Mock
    Tangle tangle;
    @Mock
    SnapshotProvider snapshotProvider;
    @Mock
    private Neighbor neighbor;
    @Mock
    TransactionViewModel tvm;

    @Test
    public void propagatesSolidTailAndValidBundle() throws Exception {
        List<TransactionViewModel> bundleTransactions = new ArrayList<TransactionViewModel>() {

            {
                add(tvm);
            }
        };
        Mockito.when(tvm.isSolid()).thenReturn(true);
        Mockito.when(tvm.getCurrentIndex()).thenReturn(0L);
        Mockito.when(bundleValidator.validate(tangle, true, snapshotProvider.getInitialSnapshot(), tvm.getHash()))
                .thenReturn(bundleTransactions);

        QuickBundleValidationStage stage = new QuickBundleValidationStage(tangle, snapshotProvider, bundleValidator,
                transactionValidator);
        QuickBundleValidationPayload payload = new QuickBundleValidationPayload(neighbor, tvm);
        ProcessingContext ctx = new ProcessingContext(null, payload);
        stage.process(ctx);

        Mockito.verify(transactionValidator).addSolidTransaction(tvm.getHash());
    }

    @Test
    public void shouldNotPropagateNonSolidTail() throws Exception {
        Mockito.when(tvm.isSolid()).thenReturn(false);
        Mockito.when(tvm.getCurrentIndex()).thenReturn(0L);
        QuickBundleValidationStage stage = new QuickBundleValidationStage(tangle, snapshotProvider, bundleValidator,
                transactionValidator);
        QuickBundleValidationPayload payload = new QuickBundleValidationPayload(neighbor, tvm);
        ProcessingContext ctx = new ProcessingContext(null, payload);
        stage.process(ctx);
        Mockito.verify(transactionValidator, Mockito.never()).addSolidTransaction(tvm.getHash());
    }

    @Test
    public void shouldNotPropagateNonTail() throws Exception {
        Mockito.when(tvm.isSolid()).thenReturn(false);
        Mockito.when(tvm.getCurrentIndex()).thenReturn(1L);
        QuickBundleValidationStage stage = new QuickBundleValidationStage(tangle, snapshotProvider, bundleValidator,
                transactionValidator);
        QuickBundleValidationPayload payload = new QuickBundleValidationPayload(neighbor, tvm);
        ProcessingContext ctx = new ProcessingContext(null, payload);
        stage.process(ctx);
        Mockito.verify(transactionValidator, Mockito.never()).addSolidTransaction(tvm.getHash());
    }

    @Test
    public void shouldNotPropagateInvalidBundle() throws Exception {
        Mockito.when(tvm.isSolid()).thenReturn(false);
        Mockito.when(tvm.getCurrentIndex()).thenReturn(1L);
        Mockito.when(bundleValidator.validate(tangle, true, snapshotProvider.getInitialSnapshot(), tvm.getHash()))
                .thenReturn(Collections.emptyList());
        QuickBundleValidationStage stage = new QuickBundleValidationStage(tangle, snapshotProvider, bundleValidator,
                transactionValidator);
        QuickBundleValidationPayload payload = new QuickBundleValidationPayload(neighbor, tvm);
        ProcessingContext ctx = new ProcessingContext(null, payload);
        stage.process(ctx);
        Mockito.verify(transactionValidator, Mockito.never()).addSolidTransaction(tvm.getHash());
    }
}
