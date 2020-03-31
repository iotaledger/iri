package com.iota.iri.network.pipeline;

import com.iota.iri.BundleValidator;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.validation.TransactionValidator;
import com.iota.iri.storage.Tangle;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link QuickBundleValidationStage} validates the transaction bundle and propagates it if it's valid.
 */
public class QuickBundleValidationStage implements Stage {

    private static final Logger log = LoggerFactory.getLogger(QuickBundleValidationStage.class);

    BundleValidator bundleValidator;
    TransactionValidator transactionValidator;
    Tangle tangle;
    SnapshotProvider snapshotProvider;

    /**
     * Creates a new {@link QuickBundleValidationStage}
     * 
     * @param tangle               Tangle
     * @param snapshotProvider     SnapshotProvider
     * @param bundleValidator      BundleValidator
     * @param transactionValidator TransactionValidator
     */
    public QuickBundleValidationStage(Tangle tangle, SnapshotProvider snapshotProvider, BundleValidator bundleValidator,
            TransactionValidator transactionValidator) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.bundleValidator = bundleValidator;
        this.transactionValidator = transactionValidator;
    }

    @Override
    public ProcessingContext process(ProcessingContext ctx) {
        QuickBundleValidationPayload payload = (QuickBundleValidationPayload) ctx.getPayload();
        TransactionViewModel tvm = payload.getTransactionViewModel();

        try {
            if (tvm.isSolid() && tvm.getCurrentIndex() == 0) {
                List<TransactionViewModel> bundleTransactions = bundleValidator.validate(tangle, true,
                        snapshotProvider.getInitialSnapshot(), tvm.getHash());
                if (!bundleTransactions.isEmpty()) {
                    transactionValidator.addSolidTransaction(tvm.getHash());
                }
            }
        } catch (Exception e) {
            log.error("error validating bundle", e);
        }
        ctx.setNextStage(TransactionProcessingPipeline.Stage.BROADCAST);
        ctx.setPayload(new BroadcastPayload(payload.getOriginNeighbor(), tvm));
        return ctx;
    }
}
