package com.iota.iri.service.milestone;

/**
 * Attempts to retrieve the milestones that have been defined in the local snapshots file.<br />
 * <br />
 * The manager incorporates a background worker that proactively requests the missing milestones until all defined
 * milestones are known. After all milestones have been retrieved the manager shuts down automatically (to free the
 * unused resources).<br />
 * <br />
 * Note: When we bootstrap a node with a local snapshot file, we are provided with a list of all seen milestones that
 *       were known during the creation of the snapshot. This list allows new nodes or nodes that start over with an
 *       empty database, to retrieve the missing milestones efficiently by directly requesting them from its neighbours
 *       (without having to wait for them to be discovered during the solidification process).<br />
 * <br />
 * This speeds up the sync-times massively and leads to nodes that are up within minutes rather than hours or even
 * days.<br />
 */
public interface SeenMilestonesRetriever {
    /**
     * Triggers the retrieval of the milestones by issuing transaction requests to the nodes neighbours.<br />
     * <br />
     * It gets periodically called by the background worker to automatically retrieve all missing milestones.<br />
     */
    void retrieveSeenMilestones();

    /**
     * Starts the background worker that automatically calls {@link #retrieveSeenMilestones()}
     * periodically to retrieves all "seen" missing milestones.<br />
     */
    void start();

    /**
     * Stops the background worker that retrieves all "seen" missing milestones.<br />
     */
    void shutdown();
}
