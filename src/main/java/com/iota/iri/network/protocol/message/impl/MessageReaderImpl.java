package com.iota.iri.network.protocol.message.impl;

import com.iota.iri.network.protocol.Protocol;
import com.iota.iri.network.protocol.message.MessageReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public class MessageReaderImpl implements MessageReader {

    private ByteBuffer msgBuf;
    private Protocol.MessageType messageType;

    public MessageReaderImpl(Protocol.MessageType messageType, short msgSize) {
        this.messageType = messageType;
        this.msgBuf = ByteBuffer.allocate(msgSize);
    }

    public boolean ready() {
        return !msgBuf.hasRemaining();
    }

    public int readMessage(ReadableByteChannel channel) throws IOException {
        return channel.read(msgBuf);
    }

    public ByteBuffer getMessage() {
        return msgBuf;
    }

    public Protocol.MessageType getMessageType() {
        return messageType;
    }
}
