package com.iota.iri.network.protocol;

/**
 * The {@link ProtocolHeader} denotes the protocol version used by the node and the TLV of the packet.
 */
public class ProtocolHeader {

    private byte version;
    private Protocol.MessageType messageType;
    private short messageSize;

    /**
     * Creates a new protocol header.
     * 
     * @param version     the version used by the node
     * @param messageType the message type
     * @param messageSize the message size
     */
    public ProtocolHeader(byte version, Protocol.MessageType messageType, short messageSize) {
        this.version = version;
        this.messageType = messageType;
        this.messageSize = messageSize;
    }

    /**
     * Gets the protocol version.
     * 
     * @return the protocol version
     */
    public int getVersion() {
        return version;
    }

    /**
     * Gets the denoted message type.
     * 
     * @return the denoted message type
     */
    public Protocol.MessageType getMessageType() {
        return messageType;
    }

    /**
     * Gets the denoted message size.
     * 
     * @return the denoted message size
     */
    public short getMessageSize() {
        return messageSize;
    }
}
