package com.iota.iri.network.pipeline;

import com.iota.iri.network.neighbor.Neighbor;

/**
 * Defines a payload which is given to a {@link ProcessingContext} for processing within a {@link Stage}.
 */
public abstract class Payload {

    /**
     * Gets the origin neighbor from which a given transaction originated from.
     * Can be null if the transaction did not originate from a neighbor.
     *
     * @return the origin neighbor
     */
    public abstract Neighbor getOriginNeighbor();
}
