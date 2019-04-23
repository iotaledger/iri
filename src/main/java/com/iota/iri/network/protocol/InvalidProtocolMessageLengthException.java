package com.iota.iri.network.protocol;

/**
 * Thrown when a packet advertises a message length which is invalid for the given {@link ProtocolMessage} type.
 */
public class InvalidProtocolMessageLengthException extends Exception {

    public InvalidProtocolMessageLengthException(String msg) {
        super(msg);
    }
}
