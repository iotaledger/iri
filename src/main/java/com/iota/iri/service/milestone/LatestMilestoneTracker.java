package com.iota.iri.service.milestone;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;

/**
 * The manager that keeps track of the latest milestone by incorporating a background worker that periodically checks if
 * new milestones have arrived.<br />
 * <br />
 * Knowing about the latest milestone and being able to compare it to the latest solid milestone allows us to determine
 * if our node is "in sync".<br />
 */
public interface LatestMilestoneTracker {
    /**
     * Returns the index of the latest milestone that was seen by this tracker.<br />
     * <br />
     * It simply returns the internal property that is used to store the latest milestone index.<br />
     *
     * @return the index of the latest milestone that was seen by this tracker
     */
    int getLatestMilestoneIndex();

    /**
     * Returns the transaction hash of the latest milestone that was seen by this tracker.<br />
     * <br />
     * It simply returns the internal property that is used to store the latest milestone index.<br />
     *
     * @return the transaction hash of the latest milestone that was seen by this tracker
     */
    Hash getLatestMilestoneHash();

    /**
     * Sets the latest milestone.<br />
     * <br />
     * It simply stores the passed in values in their corresponding internal properties and can therefore be used to
     * inform the {@link LatestSolidMilestoneTracker} about a new milestone. It is internally used to set the new
     * milestone but can also be used by tests to mock a certain behaviour or in case we detect a new milestone in other
     * parts of the code.<br />
     *
     * @param latestMilestoneHash the transaction hash of the milestone
     * @param latestMilestoneIndex the milestone index of the milestone
     */
    void setLatestMilestone(Hash latestMilestoneHash, int latestMilestoneIndex);

    /**
     * Analyzes the given transaction to determine if it is a valid milestone.<br />
     * <br />
     * If the transaction that was analyzed represents a milestone, we check if it is younger than the current latest
     * milestone and update the internal properties accordingly.<br />
     *
     * @param transaction the transaction that shall be examined
     * @return the result of the analysis translated into the corresponding {@link MilestoneValidity} status
     * @throws MilestoneException if anything unexpected happens while trying to analyze the milestone candidate
     */
    MilestoneValidity analyzeMilestoneCandidate(TransactionViewModel transaction) throws
            MilestoneException;

    /**
     * Does the same as {@link #analyzeMilestoneCandidate(TransactionViewModel)} but automatically retrieves the
     * transaction belonging to the passed in hash.<br />
     *
     * @param transactionHash the hash of the transaction that shall be examined
     * @return the result of the analysis translated into the corresponding {@link MilestoneValidity} status
     * @throws MilestoneException if anything unexpected happens while trying to analyze the milestone candidate
     */
    MilestoneValidity analyzeMilestoneCandidate(Hash transactionHash) throws MilestoneException;

    /**
     * Since the {@link LatestMilestoneTracker} scans all milestone candidates whenever IRI restarts, this flag gives us
     * the ability to determine if this initialization process has finished.<br />
     * <br />
     * The values returned by {@link #getLatestMilestoneHash()} and {@link #getLatestMilestoneIndex()} will potentially
     * return wrong values until the scan has completed.<br />
     *
     * @return {@code true} if the initial scan of milestones has finished and {@code false} otherwise
     */
    boolean isInitialScanComplete();

    /**
     * This method starts the background worker that automatically calls {@link #analyzeMilestoneCandidate(Hash)} on all
     * newly found milestone candidates to update the latest milestone.<br />
     */
    void start();

    /**
     * This method stops the background worker that updates the latest milestones.<br />
     */
    void shutdown();
}
