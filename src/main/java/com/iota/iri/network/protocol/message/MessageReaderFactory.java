package com.iota.iri.network.protocol.message;

import com.iota.iri.network.protocol.Protocol;
import com.iota.iri.network.protocol.UnknownMessageTypeException;
import com.iota.iri.network.protocol.message.impl.MessageReaderImpl;

/**
 * {@link MessageReaderFactory} provides methods to easily construct a {@link MessageReader}.
 */
public class MessageReaderFactory {

    /**
     * Creates a new {@link MessageReader} for the given message type.
     * 
     * @param msgType the message type
     * @return a {@link MessageReader} for the given message type
     * @throws UnknownMessageTypeException when the message type is nto known
     */
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

    /**
     * Creates a new {@link MessageReader} with an explicit size to read.
     *
     * @param msgType    the message type
     * @param packetSize the max bytes to read
     * @return a {@link MessageReader} for the given message type
     */
    public static MessageReader create(Protocol.MessageType msgType, short packetSize) {
        return new MessageReaderImpl(msgType, packetSize);
    }

}
