package com.iota.iri.network.protocol;

/**
 * The {@link ProtocolHeader} denotes the protocol version used by the node and the TLV of the packet.
 */
public class ProtocolHeader {

    private byte version;
    private ProtocolMessage protoMsg;
    private short messageLength;

    /**
     * Creates a new protocol header.
     * 
     * @param version       the version used by the node
     * @param protoMsg      the message type
     * @param messageLength the message length
     */
    public ProtocolHeader(byte version, ProtocolMessage protoMsg, short messageLength) {
        this.version = version;
        this.protoMsg = protoMsg;
        this.messageLength = messageLength;
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
    public ProtocolMessage getMessageType() {
        return protoMsg;
    }

    /**
     * Gets the denoted message length.
     * 
     * @return the denoted message length
     */
    public short getMessageLength() {
        return messageLength;
    }
}
