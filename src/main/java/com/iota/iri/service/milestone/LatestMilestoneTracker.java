package com.iota.iri.service.milestone;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;

/**
 * <p>
 * The manager that keeps track of the latest milestone by incorporating a background worker that periodically checks if
 * new milestones have arrived.
 * </p>
 * <p>
 * Knowing about the latest milestone and being able to compare it to the latest solid milestone allows us to determine
 * if our node is "in sync".
 * </p>
 */
public interface LatestMilestoneTracker {
    /**
     * <p>
     * Returns the index of the latest milestone that was seen by this tracker.
     * </p>
     * <p>
     * It returns the internal property that is used to store the latest milestone index.
     * </p>
     *
     * @return the index of the latest milestone that was seen by this tracker
     */
    int getLatestMilestoneIndex();

    /**
     * <p>
     * Returns the transaction hash of the latest milestone that was seen by this tracker.
     * </p>
     * <p>
     * It returns the internal property that is used to store the latest milestone index.
     * </p>
     * @return the transaction hash of the latest milestone that was seen by this tracker
     */
    Hash getLatestMilestoneHash();

    /**
     * Sets the latest milestone.
     * <p>
     * It stores the passed in values in their corresponding internal properties and can therefore be used to
     * inform the {@link LatestSolidMilestoneTracker} about a new milestone. It is internally used to set the new
     * milestone but can also be used by tests to mock a certain behaviour or in case we detect a new milestone in other
     * parts of the code.
     * </p>
     *
     * @param latestMilestoneHash the transaction hash of the milestone
     * @param latestMilestoneIndex the milestone index of the milestone
     */
    void setLatestMilestone(Hash latestMilestoneHash, int latestMilestoneIndex);

    /**
     * Analyzes the given transaction to determine if it is a valid milestone.
     * <p>
     * If the transaction that was analyzed represents a milestone, we check if it is younger than the current latest
     * milestone and update the internal properties accordingly.
     * </p>
     *
     * @param transaction the transaction that shall be examined
     * @return {@code true} if the milestone could be processed and {@code false} if the bundle is not complete, yet
     * @throws MilestoneException if anything unexpected happens while trying to analyze the milestone candidate
     */
    boolean processMilestoneCandidate(TransactionViewModel transaction) throws MilestoneException;

    /**
     * Does the same as {@link #processMilestoneCandidate(TransactionViewModel)} but automatically retrieves the
     * transaction belonging to the passed in hash.
     *
     * @param transactionHash the hash of the transaction that shall be examined
     * @return {@code true} if the milestone could be processed and {@code false} if the bundle is not complete, yet
     * @throws MilestoneException if anything unexpected happens while trying to analyze the milestone candidate
     */
    boolean processMilestoneCandidate(Hash transactionHash) throws MilestoneException;

    /**
     * <p>
     * Since the {@link LatestMilestoneTracker} scans all milestone candidates whenever IRI restarts, this flag gives us
     * the ability to determine if this initialization process has finished.
     * </p>
     * <p>
     * The values returned by {@link #getLatestMilestoneHash()} and {@link #getLatestMilestoneIndex()} will potentially
     * return wrong values until the scan has completed.
     * </p>
     *
     * @return {@code true} if the initial scan of milestones has finished and {@code false} otherwise
     */
    boolean isInitialScanComplete();

    /**
     * This method starts the background worker that automatically calls {@link #processMilestoneCandidate(Hash)} on all
     * newly found milestone candidates to update the latest milestone.
     */
    void start();

    /**
     * This method stops the background worker that updates the latest milestones.
     */
    void shutdown();
}
