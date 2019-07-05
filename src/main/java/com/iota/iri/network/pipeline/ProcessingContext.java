package com.iota.iri.network.pipeline;

/**
 * A {@link ProcessingContext} defines a context within the {@link TransactionProcessingPipelineImpl} of processing a
 * transaction. It holds the information to which stage to be submitted next and the associated payload.
 */
public class ProcessingContext {

    private TransactionProcessingPipelineImpl.Stage nextStage;
    private Payload payload;

    /**
     * Creates a new {@link ProcessingContext}.
     * 
     * @param payload The payload
     */
    public ProcessingContext(Payload payload) {
        this.payload = payload;
    }

    /**
     * Creates a new {@link ProcessingContext}.
     * 
     * @param nextStage The next stage
     * @param payload   The payload for the next stage
     */
    public ProcessingContext(TransactionProcessingPipelineImpl.Stage nextStage, Payload payload) {
        this.nextStage = nextStage;
        this.payload = payload;
    }

    /**
     * Gets the payload.
     * 
     * @return the payload
     */
    public Payload getPayload() {
        return payload;
    }

    /**
     * Sets the payload.
     * 
     * @param payload the payload to set
     */
    public void setPayload(Payload payload) {
        this.payload = payload;
    }

    /**
     * Gets the next stage.
     * 
     * @return the next stage to submit this {@link ProcessingContext} to
     */
    public TransactionProcessingPipelineImpl.Stage getNextStage() {
        return nextStage;
    }

    /**
     * Sets the next stage.
     * 
     * @param nextStage the stage to set as the next stage
     */
    public void setNextStage(TransactionProcessingPipelineImpl.Stage nextStage) {
        this.nextStage = nextStage;
    }
}
