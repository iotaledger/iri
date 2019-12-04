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

/**
 * The {@link SolidifyStage} is used to process newly received transaction for solidity. Once a transaction has been
 * passed from the {@link ReceivedStage} it will be placed into this stage to have the {@link TransactionSolidifier}
 * check the solidity of the transaction. If the transaction is found to be solid, it will be passed forward to the
 * {@link BroadcastStage}. If it is found to be unsolid, it is put through the solidity check so that missing reference
 * transactions get requested. If the transaction is unsolid, a random solid tip is broadcast instead to keep the
 * requests transmitting to neighbors.
 */
public class SolidifyStage implements Stage {
    private static final Logger log = LoggerFactory.getLogger(SolidifyStage.class);

    private TransactionSolidifier txSolidifier;
    private TransactionValidator txValidator;
    private TipsViewModel tipsViewModel;
    private Tangle tangle;

    /**
     * Constructor for the {@link SolidifyStage}.
     *
     * @param txSolidifier      Transaction solidifier implementation for updating transaction's solidity status
     * @param txValidator       Transaction validator implementation for determining the validity of a transaction
     * @param tipsViewModel     Used for broadcasting random solid tips if the subject transaction is unsolid
     * @param tangle            A reference to the nodes DB
     */
    public SolidifyStage(TransactionSolidifier txSolidifier, TransactionValidator txValidator,
                         TipsViewModel tipsViewModel, Tangle tangle){
        this.txSolidifier = txSolidifier;
        this.txValidator = txValidator;
        this.tipsViewModel = tipsViewModel;
        this.tangle = tangle;
    }

    /**
     * Processes the payload of the {@link ProcessingContext} as a {@link SolidifyPayload}. First the transaction will
     * be checked for solidity and validity. If the transaction is already solid or can be set solid quickly by the
     * transaction validator, the transaction is passed to the {@link BroadcastStage}. If not, the transaction is
     * added to the solidification queue, and a random solid tip is pulled form the {@link TipsViewModel} to be
     * broadcast instead.
     *
     * @param ctx       The context to process
     * @return          The output context, in most cases a {@link BroadcastPayload}.
     */
    @Override
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
                if(solidTip.isSolid()) {
                    ctx.setNextStage(TransactionProcessingPipeline.Stage.BROADCAST);
                    ctx.setPayload(new BroadcastPayload(payload.getOriginNeighbor(), solidTip));
                    return ctx;
                }
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
