package com.iota.iri.network.pipeline;

import com.iota.iri.model.Hash;
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
     * {@link Hash} of potential milestone object
     */
    private Hash milestoneHash;

    /**
     * Index of potential milestone object
     */
    private int milestoneIndex;

    /**
     * Constructor for a {@link MilestonePayload} object that will be processed by the {@link MilestoneStage}.
     *
     * @param originNeighbor    Neighbor that milestone candidate originated from
     * @param milestoneHash     {@link Hash} of the milestone candidate
     * @param milestoneIndex    Index of the milestone candidate
     */
    public MilestonePayload(Neighbor originNeighbor, Hash milestoneHash, int milestoneIndex){
        this.originNeighbor = originNeighbor;
        this.milestoneHash = milestoneHash;
        this.milestoneIndex = milestoneIndex;
    }

    @Override
    public Neighbor getOriginNeighbor() {
        return originNeighbor;
    }

    /**
     * @return {@link #milestoneHash}
     */
    public Hash getMilestoneHash(){
        return this.milestoneHash;
    }

    /**
     * @return {@link #milestoneIndex}
     */
    public int getMilestoneIndex(){
        return  this.milestoneIndex;
    }
}
