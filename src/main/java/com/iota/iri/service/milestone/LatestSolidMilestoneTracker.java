package com.iota.iri.service.milestone;

import com.iota.iri.service.snapshot.SnapshotProvider;

/**
 * <p>
 * This interface defines the contract for the manager that keeps track of the latest solid milestone by incorporating a
 * background worker that periodically checks for new solid milestones.
 * </p>
 * <p>
 * Whenever it finds a new solid milestone that hasn't been applied to the ledger state, yet it triggers the application
 * logic which in return updates the {@link SnapshotProvider#getLatestSnapshot()}. Since the latest solid milestone is
 * encoded in this latest snapshot of the node, this tracker does not introduce separate getters for the latest solid
 * milestone.
 * </p>
 */
public interface LatestSolidMilestoneTracker {
    /**
     * <p>
     * This method searches for new solid milestones that follow the current latest solid milestone and that have not
     * been applied to the ledger state yet and applies them.
     * </p>
     * <p>
     * It takes care of applying the solid milestones in the correct order by only allowing solid milestones to be
     * applied that are directly following our current latest solid milestone.
     * </p>
     *
     * @throws MilestoneException if anything unexpected happens while updating the latest solid milestone
     */
    void trackLatestSolidMilestone() throws MilestoneException;

    /**
     * This method starts the background worker that automatically calls {@link #trackLatestSolidMilestone()}
     * periodically to keep the latest solid milestone up to date.
     */
    void start();

    /**
     * This method stops the background worker that automatically updates the latest solid milestone.
     */
    void shutdown();
}
