package com.iota.iri.service.garbageCollector;

/**
 * This class is a template for a {@link GarbageCollector} job.
 *
 * It acts as a base class and a generic interface for processing the jobs in the {@link GarbageCollector}.
 */
abstract class GarbageCollectorJob {
    /**
     * Holds a reference to the {@link GarbageCollector} that this job belongs to.
     */
    GarbageCollector garbageCollector;

    /**
     * This method is used to inform the job about the {@link GarbageCollector} it belongs to.
     *
     * It automatically gets called when the jobs gets added to the {@link GarbageCollector}.
     *
     * @param garbageCollector GarbageCollector that this job belongs to
     */
    void registerGarbageCollector(GarbageCollector garbageCollector) {
        this.garbageCollector = garbageCollector;
    }

    /**
     * This method processes the cleanup job and performs the actual pruning.
     *
     * @throws GarbageCollectorException if something goes wrong while processing the job
     */
    abstract void process() throws GarbageCollectorException;

    /**
     * This method is used to serialize the job before it gets persisted in the {@link GarbageCollector} state file.
     *
     * @return string representing a serialized version of this job
     */
    abstract String serialize();
}
