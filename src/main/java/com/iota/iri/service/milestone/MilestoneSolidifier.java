package com.iota.iri.service.milestone;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;

import java.util.List;
import java.util.Map;

/**
 * This interface defines the contract for a manager that tries to solidify unsolid milestones by incorporating a
 * background worker that periodically checks the solidity of the milestones and issues transaction requests for the
 * missing transactions until the milestones become solid.
 */
public interface MilestoneSolidifier {
    /**
     * This method allows us to add new milestones to the solidifier that will consequently be solidified.
     *
     * @param milestoneHash Hash of the milestone that shall be solidified
     * @param milestoneIndex index of the milestone that shall be solidified
     */
    void addMilestoneCandidate(Hash milestoneHash, int milestoneIndex);

    /**
     * This method starts the background worker that asynchronously solidifies the milestones.
     */
    void start();

    /**
     * This method shuts down the background worker that asynchronously solidifies the milestones.
     */
    void shutdown();

    /**
     * This method returns the latest milestone index.
     * @return Latest Milestone Index
     */
    int getLatestMilestoneIndex();

    /**
     * This method returns the latest milestone hash.
     * @return Latest Milestone Hash
     */
    Hash getLatestMilestoneHash();

    /**
     * Checks if the MilestoneSolidifier has been initialised fully. This registers true if the solidifier has performed
     * an initial solidification of present elements in the db.
     *
     * @return Initialised state.
     */
    boolean isInitialScanComplete();

    /**
     * Add to the seen milestones queue to be held for processing solid milestones. In order to be added to the queue
     * the transaction must pass {@link MilestoneService#validateMilestone(TransactionViewModel, int)}.
     *
     * @param milestoneHash  The {@link Hash} of the seen milestone
     * @param milestoneIndex The index of the seen milestone
     */
    void addSeenMilestone(Hash milestoneHash, int milestoneIndex);

    /**
     * Removes the specified element from the unsolid milestones queue. In the event that a milestone manages to get
     * into the queue, but is later determined to be {@link MilestoneValidity#INVALID}, this method allows it to be
     * removed.
     *
     * @param milestoneHash  The {@link Hash} of the milestone to be removed
     */
    void removeFromQueues(Hash milestoneHash);

    /**
     * Set the latest milestone seen by the node.
     *
     * @param milestoneHash  The latest milestone hash
     * @param milestoneIndex The latest milestone index
     */
    void setLatestMilestone(Hash milestoneHash, int milestoneIndex);

    /**
     * Returns the oldest current milestone object in the unsolid milestone queue to be processed for solidification.
     * @return The oldest milestone hash to index mapping
     */
    List<Map.Entry<Hash, Integer>> getOldestMilestonesInQueue();

    /**
     * Set the latest milestone hash and index, publish and log the update.
     * @param oldMilestoneIndex     Previous milestone index
     * @param newMilestoneIndex     New milestone index
     * @param newMilestoneHash      New milestone hash
     */
    void registerNewMilestone(int oldMilestoneIndex, int newMilestoneIndex, Hash newMilestoneHash);
}
