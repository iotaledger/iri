package com.iota.iri.network.protocol.message.impl;

import com.iota.iri.network.protocol.ProtocolMessage;
import com.iota.iri.network.protocol.message.MessageReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * A {@link MessageReaderImpl} is a {@link MessageReader}.
 */
public class MessageReaderImpl implements MessageReader {

    private ByteBuffer msgBuf;
    private ProtocolMessage protoMsg;

    /**
     * Creates a new {@link MessageReaderImpl}.
     * @param protoMsg the message type
     * @param msgLength the message length
     */
    public MessageReaderImpl(ProtocolMessage protoMsg, short msgLength) {
        this.protoMsg = protoMsg;
        this.msgBuf = ByteBuffer.allocate(msgLength);
    }

    @Override
    public boolean ready() {
        return !msgBuf.hasRemaining();
    }

    @Override
    public int readMessage(ReadableByteChannel channel) throws IOException {
        return channel.read(msgBuf);
    }

    @Override
    public ByteBuffer getMessage() {
        return msgBuf;
    }

    @Override
    public ProtocolMessage getMessageType() {
        return protoMsg;
    }
}
