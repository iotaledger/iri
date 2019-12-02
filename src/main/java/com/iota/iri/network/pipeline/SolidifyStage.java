package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.validation.TransactionSolidifier;
import com.iota.iri.service.validation.TransactionValidator;
import com.iota.iri.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.iota.iri.controllers.TransactionViewModel.fromHash;

public class SolidifyStage implements Stage {
    private static final Logger log = LoggerFactory.getLogger(SolidifyStage.class);

    private TransactionSolidifier txSolidifier;
    private TransactionValidator txValidator;
    private TipsViewModel tipsViewModel;
    private Tangle tangle;

    public SolidifyStage(TransactionSolidifier txSolidifier, TransactionValidator txValidator,
                         TipsViewModel tipsViewModel, Tangle tangle){
        this.txSolidifier = txSolidifier;
        this.txValidator = txValidator;
        this.tipsViewModel = tipsViewModel;
        this.tangle = tangle;
    }

    public ProcessingContext process(ProcessingContext ctx){
        try {
            SolidifyPayload payload = (SolidifyPayload) ctx.getPayload();
            TransactionViewModel tvm = payload.getTransaction();

            if (tvm.isSolid() || txValidator.quickSetSolid(tvm)) {
                ctx.setNextStage(TransactionProcessingPipeline.Stage.BROADCAST);
                ctx.setPayload(new BroadcastPayload(payload.getOriginNeighbor(), payload.getTransaction()));
                return ctx;
            }

            txSolidifier.addToSolidificationQueue(tvm.getHash());

            Hash tipHash = tipsViewModel.getRandomSolidTipHash();
            if(tipHash != null) {
                TransactionViewModel solidTip = fromHash(tangle, tipHash);
                ctx.setNextStage(TransactionProcessingPipeline.Stage.BROADCAST);
                ctx.setPayload(new BroadcastPayload(payload.getOriginNeighbor(), solidTip));
                return ctx;
            }

            ctx.setNextStage(TransactionProcessingPipeline.Stage.FINISH);
            return ctx;
        }catch (Exception e){
            log.error("Failed to process transaction for solidification", e);
            ctx.setNextStage(TransactionProcessingPipeline.Stage.ABORT);
            return ctx;
        }

    }



}
