package com.iota.iri.network.pipeline;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * A queue for transactions intended to be submitted to the {@link BroadcastStage}
 * for processing
 */
public class BroadcastQueue {

    /** A blocking queue to store transactions for broadcasting */
    private BlockingQueue<ProcessingContext> broadcastStageQueue = new ArrayBlockingQueue<>(100);

    /** An object to be used for synchronizing calls */
    private final Object broadcastSync = new Object();


    /**
     * Add transactions to the Broadcast Queue
     * @param context Transaction context to be passed to the {@link BroadcastStage}
     * @return True if added properly, False if not
     */
    public boolean add(ProcessingContext context) {
        synchronized (broadcastSync) {
            try {
                this.broadcastStageQueue.put(context);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

    }

    /**
     * Getter for the current Broadcast Queue
     * @return BlockingQueue of all transactions left to be broadcasted
     */
    public BlockingQueue<ProcessingContext> get(){
        /** Call is synchronized to ensure all pending additions have completed before sending the state */
        synchronized (broadcastSync) {
            return this.broadcastStageQueue;
        }
    }
}