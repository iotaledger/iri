package com.iota.iri.network.protocol;

import com.iota.iri.network.protocol.Protocol.MessageType;

/**
 * Thrown when an unknown {@link MessageType} is advertised in a packet.
 */
public class UnknownMessageTypeException extends Exception {

    public UnknownMessageTypeException(String msg) {
        super(msg);
    }
}
