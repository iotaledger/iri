package com.iota.iri.network.pipeline;

public class ProcessingContext<T> {
    private TxPipeline.Stage nextStage;
    private T payload;

    public ProcessingContext(T payload) {
        this.payload = payload;
    }

    public ProcessingContext(TxPipeline.Stage nextStage, T payload) {
        this.nextStage = nextStage;
        this.payload = payload;
    }


    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }

    public TxPipeline.Stage getNextStage() {
        return nextStage;
    }

    public void setNextStage(TxPipeline.Stage nextStage) {
        this.nextStage = nextStage;
    }
}
