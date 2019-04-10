package com.iota.iri.network.protocol;

import com.iota.iri.network.protocol.Protocol.MessageSize;

/**
 * Thrown when a packet advertises a message size over the maximum as defined per {@link MessageSize}.
 */
public class AdvertisedMessageSizeTooBigException extends Exception {

    public AdvertisedMessageSizeTooBigException(String msg) {
        super(msg);
    }
}
