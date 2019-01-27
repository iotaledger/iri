package com.iota.iri.network.spam;

import com.iota.iri.network.Neighbor;

import java.util.List;

/**
 * A spam prevention strategy tries to find the neighbors that are currently sending too much traffic.
 */
public interface SpamPreventionStrategy {

    /**
     * Recalculates what neighbors are currently spamming.
     * @param neighbors The list of neighbors that should be examined for spamming neighbors.
     */
    void calculateSpam(List<Neighbor> neighbors);

    /**
     * Checks if a neighbor's transaction should be propagated or if the neighbor is currently spamming and the
     * transaction should be blocked. Can return different results for several invocations, if for example only
     * some transactions from this neighbor should be filtered.
     * @param neighbor The neighbor to be checked.
     * @return <code>true</code> if the transaction should be relayed to other neighbors.
     */
    boolean broadcastTransactionFrom(Neighbor neighbor);

}
