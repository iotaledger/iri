package com.iota.iri.network.protocol;

/**
 * Thrown when an unknown {@link ProtocolMessage} type is advertised in a packet.
 */
public class UnknownMessageTypeException extends Exception {

    public UnknownMessageTypeException(String msg) {
        super(msg);
    }
}
