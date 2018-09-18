package com.iota.iri.service.garbagecollector;

import java.util.ArrayDeque;

/**
 * Functional interface for the lambda function that takes care of processing a queue.
 *
 * It is used to offer an interface for generically processing the different jobs that are supported by the
 * {@link GarbageCollector}.
 *
 * @see GarbageCollector#registerQueueProcessor(Class, QueueProcessor) to register the processor
 */
@FunctionalInterface
public interface QueueProcessor {
    void processQueue(GarbageCollector garbageCollector, ArrayDeque<GarbageCollectorJob> jobQueue) throws GarbageCollectorException;
}
