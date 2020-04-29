package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.validation.TransactionSolidifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Stage for processing {@link MilestonePayload} objects
 */
public class MilestoneStage implements Stage {

    private static final Logger log = LoggerFactory.getLogger(MilestoneStage.class);


    private MilestoneSolidifier milestoneSolidifier;
    private SnapshotProvider snapshotProvider;
    private TransactionSolidifier transactionSolidifier;

    /**
     * Constructor for {@link MilestoneStage}. This stage will process {@link MilestonePayload} candidate objects using
     * the {@link MilestoneSolidifier} to determine the latest milestone, the latest solid milestone, and place unsolid
     * milestone candidates into a queue for solidification. The stage will always try to solidify the oldest milestone
     * candidate in the queue.
     *
     * @param milestoneSolidifier       Solidification service for processing milestone objects
     * @param snapshotProvider          Snapshot provider service for latest snapshot references
     * @param transactionSolidifier     A service for solidifying transactions
     * @param milestoneSolidifier    Tracks the latest milestone object
     */
    public MilestoneStage(MilestoneSolidifier milestoneSolidifier,
                          SnapshotProvider snapshotProvider, TransactionSolidifier transactionSolidifier) {
        this.milestoneSolidifier = milestoneSolidifier;
        this.snapshotProvider = snapshotProvider;
        this.transactionSolidifier = transactionSolidifier;
    }

    /**
     * Process {@link MilestonePayload} objects. While processing the {@link MilestoneStage} will determine the latest
     * milestone and log it. If the milestone object passes a validity check, it is added to the seenMilestones queue in
     * the {@link MilestoneSolidifier}, whereas if it is invalid, the transaction is ignored. The milestone is then
     * checked for solidity using the {@link TransactionSolidifier}. If the transaction is solid it is passed forward to
     * the {@link TransactionSolidifier} propagation queue.
     *
     * @param ctx the context to process
     * @return Either an abort or solidify stage ctx
     */
    @Override
    public ProcessingContext process(ProcessingContext ctx) {
        try {
            MilestonePayload payload = (MilestonePayload) ctx.getPayload();

            //If the milestone index is below the latest snapshot initial index, then abort the process
            //Exempts index 0, as milestone objects don't require both transactions to hold the index
            if (payload.getMilestoneIndex() < snapshotProvider.getLatestSnapshot().getInitialIndex() &&
                    payload.getMilestoneIndex() != 0) {
              return abort(ctx);
            }

            TransactionViewModel milestone = payload.getMilestoneTransaction();
            int newMilestoneIndex = payload.getMilestoneIndex();
            boolean isTail = (milestone.getCurrentIndex() == 0);

            // Add milestone tails to the milestone solidifier, if transaction is solid, add to the propagation queue
            if (isTail) {
                milestoneSolidifier.addMilestoneCandidate(milestone.getHash(), newMilestoneIndex);
            }

            if (milestone.isSolid()) {
                transactionSolidifier.addToPropagationQueue(milestone.getHash());
            }

            return solidify(ctx, payload, milestone);
        } catch (Exception e) {
            log.error("Error processing milestone: ", e);
            return abort(ctx);
        }
    }


    private ProcessingContext solidify(ProcessingContext ctx, Payload payload, TransactionViewModel tvm) {
        SolidifyPayload solidifyPayload = new SolidifyPayload(payload.getOriginNeighbor(), tvm);
        ctx.setNextStage(TransactionProcessingPipeline.Stage.SOLIDIFY);
        ctx.setPayload(solidifyPayload);
        return ctx;
    }

    private ProcessingContext abort(ProcessingContext ctx) {
        ctx.setNextStage(TransactionProcessingPipeline.Stage.ABORT);
        return ctx;
    }
}
