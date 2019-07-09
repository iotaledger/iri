package com.iota.iri.network.neighbor;

/**
 * Defines the different states a {@link Neighbor} can be in.
 */
public enum NeighborState {
    HANDSHAKING,
    READY_FOR_MESSAGES,
    MARKED_FOR_DISCONNECT,
}
