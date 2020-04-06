package com.iota.iri.network.pipeline;

import com.iota.iri.network.neighbor.Neighbor;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;

/**
 * A pipeline using stages to process incoming transaction data from neighbors and API calls.
 */
public interface TransactionProcessingPipeline {

    /**
     * Defines the different stages of the {@link TransactionProcessingPipelineImpl}.
     */
    enum Stage {
        PRE_PROCESS, HASHING, VALIDATION, REPLY, RECEIVED, BROADCAST, MULTIPLE, ABORT, FINISH, SOLIDIFY,
    }

    /**
     * Kicks of the pipeline by assembling the pipeline and starting all threads.
     */
    void start();

    /**
     * Gets the received stage queue.
     *
     * @return the received stage queue
     */
    BlockingQueue<ProcessingContext> getReceivedStageQueue();

    /**
     * Gets the broadcast stage queue.
     *
     * @return the broadcast stage queue.
     */
    BlockingQueue<ProcessingContext> getBroadcastStageQueue();

    /**
     * Gets the reply stage queue.
     *
     * @return the reply stage queue
     */
    BlockingQueue<ProcessingContext> getReplyStageQueue();

    /**
     * Gets the validation stage queue.
     *
     * @return the validation stage queue
     */
    BlockingQueue<ProcessingContext> getValidationStageQueue();

    /**
     * Submits the given data from the given neighbor into the pre processing stage of the pipeline.
     *
     * @param neighbor the {@link Neighbor} from which the data originated from
     * @param data     the data to process
     */
    void process(Neighbor neighbor, ByteBuffer data);

    /**
     * Submits the given transactions trits into the hashing stage of the pipeline.
     *
     * @param txTrits the transaction trits
     */
    void process(byte[] txTrits);

    /**
     * Shut downs the pipeline by shutting down all stages.
     */
    void shutdown();

    /**
     * Sets the pre process stage. This method should only be used for injecting mocked objects.
     *
     * @param preProcessStage the {@link PreProcessStage} to use
     */
    void setPreProcessStage(PreProcessStage preProcessStage);

    /**
     * Sets the validation stage. This method should only be used for injecting mocked objects.
     *
     * @param receivedStage the {@link ReceivedStage} to use
     */
    void setReceivedStage(ReceivedStage receivedStage);

    /**
     * Sets the validation stage. This method should only be used for injecting mocked objects.
     *
     * @param validationStage the {@link ValidationStage} to use
     */
    void setValidationStage(ValidationStage validationStage);

    /**
     * Sets the reply stage. This method should only be used for injecting mocked objects.
     *
     * @param replyStage the {@link ReplyStage} to use
     */
    void setReplyStage(ReplyStage replyStage);

    /**
     * Sets the broadcast stage. This method should only be used for injecting mocked objects.
     *
     * @param broadcastStage the {@link BroadcastStage} to use
     */
    void setBroadcastStage(BroadcastStage broadcastStage);

    /**
     * Sets the hashing stage. This method should only be used for injecting mocked objects.
     *
     * @param hashingStage the {@link HashingStage} to use
     */
    void setHashingStage(HashingStage hashingStage);

    /**
     * Sets the solidify stage. This method should only be used for injecting mocked objects.
     *
     * @param solidifyStage the {@link SolidifyStage} to use
     */
    void setSolidifyStage(SolidifyStage solidifyStage);
}