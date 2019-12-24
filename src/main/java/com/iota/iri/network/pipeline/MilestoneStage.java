package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.service.milestone.MilestoneService;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.validation.TransactionSolidifier;
import com.iota.iri.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Stage for processing {@link MilestonePayload} objects
 */
public class MilestoneStage implements Stage {

    private static final Logger log = LoggerFactory.getLogger(MilestoneStage.class);


    private MilestoneSolidifier milestoneSolidifier;
    private Tangle tangle;
    private SnapshotProvider snapshotProvider;
    private MilestoneService milestoneService;
    private TransactionSolidifier transactionSolidifier;

    /**
     * Constructor for {@link MilestoneStage}. This stage will process {@link MilestonePayload} candidate objects using
     * the {@link MilestoneSolidifier} to determine the latest milestone, the latest solid milestone, and place unsolid
     * milestone candidates into a queue for solidification. The stage will always try to solidify the oldest milestone
     * candidate in the queue.
     *
     * @param tangle                    The tangle reference
     * @param milestoneSolidifier       Solidification service for processing milestone objects
     * @param snapshotProvider          Snapshot provider service for latest snapshot references
     * @param milestoneService          A service for validating milestone objects
     * @param transactionSolidifier     A service for solidifying transactions
     */
    public MilestoneStage(Tangle tangle, MilestoneSolidifier milestoneSolidifier,
                          SnapshotProvider snapshotProvider, MilestoneService milestoneService,
                          TransactionSolidifier transactionSolidifier){
        this.milestoneSolidifier = milestoneSolidifier;
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.milestoneService = milestoneService;
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
    public ProcessingContext process(ProcessingContext ctx){
        try {
            MilestonePayload payload = (MilestonePayload) ctx.getPayload();

            //If the milestone index is below the latest snapshot initial index, then abort the process
            if(payload.getMilestoneIndex() < snapshotProvider.getLatestSnapshot().getInitialIndex()){
              return abort(ctx);
            }

            TransactionViewModel milestone = TransactionViewModel.fromHash(tangle, payload.getMilestoneHash());
            int newMilestoneIndex = payload.getMilestoneIndex();
            boolean isFirstInBundle = (milestone.getCurrentIndex() == 0);

            // Log new milestones
            int latestMilestoneIndex = milestoneSolidifier.getLatestMilestoneIndex();
            if (newMilestoneIndex > latestMilestoneIndex) {
                milestoneSolidifier.logNewMilestone(latestMilestoneIndex, newMilestoneIndex, milestone.getHash());
            }

            // Add unsolid milestones to the milestone solidifier, or add solid milestones to the propagation queue
            if (!transactionSolidifier.addMilestoneToSolidificationQueue(milestone.getHash(), 50000) &&
            isFirstInBundle) {
                milestoneSolidifier.add(milestone.getHash(), payload.getMilestoneIndex());
            } else {
                transactionSolidifier.addToPropagationQueue(milestone.getHash());
            }

            return solidify(ctx, payload, milestone);
        }catch (Exception e){
            log.error("Error processing milestone: ", e);
            return abort(ctx);
        }
    }


    private ProcessingContext solidify(ProcessingContext ctx, Payload payload, TransactionViewModel tvm){
        SolidifyPayload solidifyPayload = new SolidifyPayload(payload.getOriginNeighbor(), tvm);
        ctx.setNextStage(TransactionProcessingPipeline.Stage.SOLIDIFY);
        ctx.setPayload(solidifyPayload);
        return ctx;
    }

    private ProcessingContext abort(ProcessingContext ctx){
        ctx.setNextStage(TransactionProcessingPipeline.Stage.ABORT);
        return ctx;
    }
}
