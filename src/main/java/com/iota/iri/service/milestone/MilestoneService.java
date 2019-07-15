package com.iota.iri.service.milestone;

import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;

import java.util.Optional;

/**
 * <p>
 * Represents the service that contains all the relevant business logic for interacting with milestones.
 * </p>
 * This class is stateless and does not hold any domain specific models.
 */
public interface MilestoneService {
    /**
     * <p>
     * Finds the latest solid milestone that was previously processed by IRI (before a restart) by performing a search
     * in the database.
     * </p>
     * <p>
     * It determines if the milestones were processed by checking the {@code snapshotIndex} value of their corresponding
     * transactions.
     * </p>
     * 
     * @return the latest solid milestone that was previously processed by IRI or an empty value if no previously
     *         processed solid milestone can be found
     * @throws MilestoneException if anything unexpected happend while performing the search
     */
    Optional<MilestoneViewModel> findLatestProcessedSolidMilestoneInDatabase() throws MilestoneException;

    /**
     * <p>
     * Analyzes the given transaction to determine if it is a valid milestone.
     * </p>
     * </p>
     * It first checks if all transactions that belong to the milestone bundle are known already and only then verifies
     * the signature to analyze if the given milestone was really issued by the coordinator.
     * </p>
     * 
     * @param transactionViewModel transaction that shall be analyzed
     * @param milestoneIndex milestone index of the transaction (see {@link #getMilestoneIndex(TransactionViewModel)})
     * @return validity status of the transaction regarding its role as a milestone
     * @throws MilestoneException if anything unexpected goes wrong while validating the milestone transaction
     */
    MilestoneValidity validateMilestone(TransactionViewModel transactionViewModel, int milestoneIndex)
            throws MilestoneException;

    /**
     * <p>
     * Updates the milestone index of all transactions that belong to a milestone.
     * </p>
     * <p>
     * It does that by iterating through all approvees of the milestone defined by the given {@code milestoneHash} until
     * it reaches transactions that have been approved by a previous milestone. This means that this method only works
     * if the transactions belonging to the previous milestone have been updated already.
     * </p>
     * <p>
     * While iterating through the transactions we also examine the milestone index that is currently set to detect
     * corruptions in the database where a following milestone was processed before the current one. If such a
     * corruption in the database is found we trigger a reset of the wrongly processed milestone to repair the ledger
     * state and recover from this error.
     * </p>
     * <p>
     * In addition to these checks we also update the solid entry points, if we detect that a transaction references a
     * transaction that dates back before the last local snapshot (and that has not been marked as a solid entry point,
     * yet). This allows us to handle back-referencing transactions and maintain local snapshot files that can always be
     * used to bootstrap a node, even if the coordinator suddenly approves really old transactions (this only works
     * if the transaction that got referenced is still "known" to the node by having a sufficiently high pruning
     * delay).
     * </p>
     *
     * @param milestoneHash the hash of the transaction
     * @param newIndex the milestone index that shall be set
     * @throws MilestoneException if anything unexpected happens while updating the milestone index
     */
    void updateMilestoneIndexOfMilestoneTransactions(Hash milestoneHash, int newIndex) throws MilestoneException;

    /**
     * <p>
     * Resets all milestone related information of the transactions that were "confirmed" by the given milestone and
     * rolls back the ledger state to the moment before the milestone was applied.
     * </p>
     * <p>
     * This allows us to reprocess the milestone in case of errors where the given milestone could not be applied to the
     * ledger state. It is for example used by the automatic repair routine of the {@link LatestSolidMilestoneTracker}
     * (to recover from inconsistencies due to crashes of IRI).
     * </p>
     * <p>
     * It recursively resets additional milestones if inconsistencies are found within the resetted milestone (wrong
     * {@code milestoneIndex}es).
     * </p>
     * 
     * @param index milestone index that shall be reverted
     * @throws MilestoneException if anything goes wrong while resetting the corrupted milestone
     */
    void resetCorruptedMilestone(int index) throws MilestoneException;

    /**
     * <p>
     * Checks if the given transaction was confirmed by the milestone with the given index (or any of its
     * predecessors).
     * </p>
     * </p>
     * We determine if the transaction was confirmed by examining its {@code snapshotIndex} value. For this method to
     * work we require that the previous milestones have been processed already (which is enforced by the {@link
     * com.iota.iri.service.milestone.LatestSolidMilestoneTracker} which applies the milestones in the order that they
     * are issued by the coordinator).
     * </p>
     *
     * @param transaction the transaction that shall be examined
     * @param milestoneIndex the milestone index that we want to check against
     * @return {@code true} if the transaction belongs to the milestone and {@code false} otherwise
     */
    boolean isTransactionConfirmed(TransactionViewModel transaction, int milestoneIndex);

    /**
     * Does the same as {@link #isTransactionConfirmed(TransactionViewModel, int)} but defaults to the latest solid
     * milestone index for the {@code milestoneIndex} which means that the transaction has been included in our current
     * ledger state.
     *
     * @param transaction the transaction that shall be examined
     * @return {@code true} if the transaction belongs to the milestone and {@code false} otherwise
     */
    boolean isTransactionConfirmed(TransactionViewModel transaction);

    /**
     * <p>
     * Retrieves the milestone index of the given transaction by decoding the {@code OBSOLETE_TAG}.
     * </p>
     * <p>
     * The returned result will of cause only have a reasonable value if we hand in a transaction that represents a real
     * milestone.
     * </p>
     * 
     * @param milestoneTransaction the transaction that shall have its milestone index retrieved
     * @return the milestone index of the transaction
     */
    int getMilestoneIndex(TransactionViewModel milestoneTransaction);
}
