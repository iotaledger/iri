package com.iota.iri.network.protocol.message;

import com.iota.iri.network.protocol.Protocol;
import com.iota.iri.network.protocol.UnknownMessageTypeException;
import com.iota.iri.network.protocol.message.impl.MessageReaderImpl;

public class MessageReaderFactory {

    public static MessageReader create(Protocol.MessageType msgType) throws UnknownMessageTypeException {
        switch (msgType) {
            case HEADER:
                return create(msgType, Protocol.MessageSize.HEADER.getSize());
            case HANDSHAKE:
                return create(msgType, Protocol.MessageSize.HANDSHAKE.getSize());
            case TRANSACTION_GOSSIP:
                return create(msgType, Protocol.MessageSize.TRANSACTION_GOSSIP.getSize());
            default:
                throw new UnknownMessageTypeException("can't construct MessageReaderImpl for unknown message type");
        }
    }

    public static MessageReader create(Protocol.MessageType msgType, short packetSize) throws UnknownMessageTypeException {
        return new MessageReaderImpl(msgType, packetSize);
    }

}
