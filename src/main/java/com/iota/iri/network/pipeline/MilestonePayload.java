package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.neighbor.Neighbor;

/**
 * A payload object for processing milestone candidates
 */
public class MilestonePayload extends Payload {
    /**
     * Origin neighbor for transaction
     */
    private Neighbor originNeighbor;

    /**
     * {@link TransactionViewModel} of potential milestone object
     */
    private TransactionViewModel milestoneTransaction;

    /**
     * Index of potential milestone object
     */
    private int milestoneIndex;

    /**
     * Constructor for a {@link MilestonePayload} object that will be processed by the {@link MilestoneStage}.
     *
     * @param originNeighbor        Neighbor that milestone candidate originated from
     * @param milestoneTransaction  {@link TransactionViewModel} of the milestone candidate
     * @param milestoneIndex        Index of the milestone candidate
     */
    public MilestonePayload(Neighbor originNeighbor, TransactionViewModel milestoneTransaction, int milestoneIndex){
        this.originNeighbor = originNeighbor;
        this.milestoneTransaction = milestoneTransaction;
        this.milestoneIndex = milestoneIndex;
    }

    @Override
    public Neighbor getOriginNeighbor() {
        return originNeighbor;
    }

    /**
     * @return {@link #milestoneTransaction}
     */
    public TransactionViewModel getMilestoneTransaction(){
        return this.milestoneTransaction;
    }

    /**
     * @return {@link #milestoneIndex}
     */
    public int getMilestoneIndex(){
        return  this.milestoneIndex;
    }
}
