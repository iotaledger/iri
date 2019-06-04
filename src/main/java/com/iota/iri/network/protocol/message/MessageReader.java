package com.iota.iri.network.protocol.message;

import com.iota.iri.network.protocol.ProtocolMessage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * A {@link MessageReader} reads up to N defined bytes from a {@link ReadableByteChannel}.
 */
public interface MessageReader {

    /**
     * Checks whether the underlying {@link ByteBuffer} is ready.
     * 
     * @return whether the {@link MessageReader}'s {@link ByteBuffer} is ready
     */
    boolean ready();

    /**
     * Reads bytes from the given channel into the {@link ByteBuffer}.
     * 
     * @param channel the channel to read from
     * @return how many bytes have been read into the buffer.
     * @throws IOException thrown when reading from the channel fails
     */
    int readMessage(ReadableByteChannel channel) throws IOException;

    /**
     * Gets the {@link ByteBuffer} holding the message.
     * 
     * @return the {@link ByteBuffer} holding the message.
     */
    ByteBuffer getMessage();

    /**
     * Gets the message type this {@link MessageReader} is expecting.
     * 
     * @return the message type this {@link MessageReader} is expecting
     */
    ProtocolMessage getMessageType();

}
