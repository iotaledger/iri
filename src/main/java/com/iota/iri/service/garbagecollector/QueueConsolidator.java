package com.iota.iri.service.garbagecollector;

import java.util.ArrayDeque;

/**
 * Functional interface for the lambda function that takes care of consolidating a queue.
 *
 * It is mainly used to merge multiple jobs that share the same status into a single one that covers all merged jobs and
 * therefore reduce the memory footprint and file size of the garbage collector state file.
 *
 * The consolidation of the queues is optional and does not have to be supported by every job type.
 *
 * @see GarbageCollector#registerQueueConsolidator(Class, QueueConsolidator) to register the consolidator
 */
@FunctionalInterface
public interface QueueConsolidator {
    void consolidateQueue(GarbageCollector garbageCollector, ArrayDeque<GarbageCollectorJob> jobQueue) throws GarbageCollectorException;
}
