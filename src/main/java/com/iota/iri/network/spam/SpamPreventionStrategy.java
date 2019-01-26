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
     * Checks if a neighbor is currently spamming and needs to be blocked.
     * @param neighbor The neighbor to be checked.
     * @return <code>true</code> if the neighbor is spamming.
     */
    boolean isSpamming(Neighbor neighbor);

}
