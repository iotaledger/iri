package com.iota.iri.network.protocol;

/**
 * Thrown when an unknown {@link ProtocolMessage} type is advertised in a packet.
 */
public class UnknownMessageTypeException extends Exception {

    /**
     * Creates a new exception for when an unknown message type is advertised.
     * @param msg the message for this exception
     */
    public UnknownMessageTypeException(String msg) {
        super(msg);
    }
}
