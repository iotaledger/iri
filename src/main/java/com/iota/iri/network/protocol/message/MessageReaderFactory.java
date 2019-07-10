package com.iota.iri.network.protocol.message;

import com.iota.iri.network.protocol.ProtocolMessage;
import com.iota.iri.network.protocol.UnknownMessageTypeException;
import com.iota.iri.network.protocol.message.impl.MessageReaderImpl;

/**
 * {@link MessageReaderFactory} provides methods to easily construct a {@link MessageReader}.
 */
public class MessageReaderFactory {

    /**
     * Creates a new {@link MessageReader} for the given message type.
     * 
     * @param protoMsg the message type
     * @return a {@link MessageReader} for the given message type
     * @throws UnknownMessageTypeException when the message type is not known
     */
    public static MessageReader create(ProtocolMessage protoMsg) throws UnknownMessageTypeException {
        switch (protoMsg) {
            case HEADER:
            case HANDSHAKE:
            case TRANSACTION_GOSSIP:
                return create(protoMsg, protoMsg.getMaxLength());
            // there might be message types in the future which need a separate message reader implementation
            default:
                throw new UnknownMessageTypeException("can't construct MessageReaderImpl for unknown message type");
        }
    }

    /**
     * Creates a new {@link MessageReader} with an explicit length to read.
     *
     * @param protoMsg     the message type
     * @param messageLength the max bytes to read
     * @return a {@link MessageReader} for the given message type
     */
    public static MessageReader create(ProtocolMessage protoMsg, short messageLength) {
        return new MessageReaderImpl(protoMsg, messageLength);
    }

}
