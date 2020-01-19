package com.iota.iri.service.transactionpruning;

/**
 * Condition that specifies if and on what milestone pruning happens.
 */
public interface PruningCondition {

    /**
     * Executes a check on the node to determine if we should prune
     *
     * @return <code>true</code> if we should start pruning, otherwise <code>false</code>
     */
    boolean shouldPrune() throws TransactionPruningException;

    /**
     * Determines the milestone at which we should start pruning.
     * This does not take the minimum amount of milestones in the database into account.
     *
     * @return The index of the last milestone we will keep in the database.
     *         Everything before this number will be pruned away if allowed.
     * @throws TransactionPruningException if we could not obtain the requirements for determining the milestone index
     */
    int getSnapshotPruningMilestone() throws TransactionPruningException;
}
