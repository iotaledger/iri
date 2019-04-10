package com.iota.iri.network.protocol;

import com.iota.iri.network.neighbor.Neighbor;

/**
 * Thrown when a {@link Neighbor} sends packets with an incompatible protocol version number.
 */
public class IncompatibleProtocolVersionException extends Exception {

    public IncompatibleProtocolVersionException(String msg) {
        super(msg);
    }
}
