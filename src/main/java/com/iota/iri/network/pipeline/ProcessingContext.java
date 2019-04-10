package com.iota.iri.network.pipeline;

/**
 * A {@link ProcessingContext} defines a context within the {@link TransactionProcessingPipeline} of processing a
 * transaction. It holds the information to which stage to be submitted next and the associated payload.
 * 
 * @param <T> the payload type
 */
public class ProcessingContext<T> {

    private TransactionProcessingPipeline.Stage nextStage;
    private T payload;

    /**
     * Creates a new {@link ProcessingContext}.
     * 
     * @param payload The payload
     */
    public ProcessingContext(T payload) {
        this.payload = payload;
    }

    /**
     * Creates a new {@link ProcessingContext}.
     * 
     * @param nextStage The next stage
     * @param payload   The payload for the next stage
     */
    public ProcessingContext(TransactionProcessingPipeline.Stage nextStage, T payload) {
        this.nextStage = nextStage;
        this.payload = payload;
    }

    /**
     * Gets the payload.
     * 
     * @return the payload
     */
    public T getPayload() {
        return payload;
    }

    /**
     * Sets the payload.
     * 
     * @param payload the payload to set
     */
    public void setPayload(T payload) {
        this.payload = payload;
    }

    /**
     * Gets the next stage.
     * 
     * @return the next stage to submit this {@link ProcessingContext} to
     */
    public TransactionProcessingPipeline.Stage getNextStage() {
        return nextStage;
    }

    /**
     * Sets the next stage.
     * 
     * @param nextStage the stage to set as the next stage
     */
    public void setNextStage(TransactionProcessingPipeline.Stage nextStage) {
        this.nextStage = nextStage;
    }
}
