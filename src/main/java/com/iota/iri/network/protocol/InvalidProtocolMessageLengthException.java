package com.iota.iri.network.protocol;

/**
 * Thrown when a packet advertises a message length which is invalid for the given {@link ProtocolMessage} type.
 */
public class InvalidProtocolMessageLengthException extends Exception {

    /**
     * Constructs a new exception for invlaid protocol message lengths.
     * 
     * @param msg the message for this exception
     */
    public InvalidProtocolMessageLengthException(String msg) {
        super(msg);
    }
}
