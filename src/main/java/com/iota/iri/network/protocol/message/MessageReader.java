package com.iota.iri.network.protocol.message;

import com.iota.iri.network.protocol.Protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public interface MessageReader {

    boolean ready();

    int readMessage(ReadableByteChannel channel) throws IOException;

    ByteBuffer getMessage();

    Protocol.MessageType getMessageType();

}
