package com.iota.iri.service.milestone;

import com.iota.iri.model.Hash;
import com.iota.iri.service.validation.TransactionSolidifier;

/**
 * This interface defines the contract for a manager that tries to solidify unsolid milestones by incorporating a
 * background worker that periodically checks the solidity of the milestones and issues transaction requests for the
 * missing transactions until the milestones become solid.
 */
public interface MilestoneSolidifier {

    /**
     * <p>
     * Defines the maximum amount of transactions that are allowed to get processed while trying to solidify a
     * milestone.
     * </p>
     * <p>
     * Note: We want to find the next previous milestone and not get stuck somewhere at the end of the tangle with a
     *       long running {@link TransactionSolidifier#checkSolidity(Hash)} call.
     * </p>
     */
    int SOLIDIFICATION_TRANSACTIONS_LIMIT = 50000;

    /**
     * This method allows us to add new milestones to the solidifier that will consequently be solidified.
     *
     * @param milestoneHash Hash of the milestone that shall be solidified
     * @param milestoneIndex index of the milestone that shall be solidified
     */
    void add(Hash milestoneHash, int milestoneIndex);

    /**
     * This method starts the background worker that asynchronously solidifies the milestones.
     */
    void start();

    /**
     * This method shuts down the background worker that asynchronously solidifies the milestones.
     */
    void shutdown();
}
