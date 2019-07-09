package com.iota.iri.network.pipeline;

import com.iota.iri.network.neighbor.Neighbor;

/**
 * A payload which contains processing contexts for two stages.
 */
public class MultiStagePayload extends Payload {

    private ProcessingContext left;
    private ProcessingContext right;

    /**
     * Creates a new {@link MultiStagePayload} with the given left and right assigned contexts.
     * 
     * @param left  the left assigned context
     * @param right the right assigned context
     */
    public MultiStagePayload(ProcessingContext left, ProcessingContext right) {
        this.left = left;
        this.right = right;
    }

    /**
     * Returns the left assigned context.
     * 
     * @return the left assigned context
     */
    public ProcessingContext getLeft() {
        return left;
    }

    /**
     * Returns the right assigned context.
     *
     * @return the right assigned context
     */
    public ProcessingContext getRight() {
        return right;
    }

    @Override
    public Neighbor getOriginNeighbor() {
        return left.getPayload().getOriginNeighbor();
    }

    @Override
    public String toString() {
        return "MultiStagePayload{" + "left=" + left.getPayload().toString() + ", right="
                + right.getPayload().toString() + '}';
    }
}
