package com.iota.iri.network.protocol;

/**
 * The {@link ProtocolHeader} denotes the protocol version used by the node and the TLV of the packet.
 */
public class ProtocolHeader {

    private ProtocolMessage protoMsg;
    private short messageLength;

    /**
     * Creates a new protocol header.
     *
     * @param protoMsg      the message type
     * @param messageLength the message length
     */
    public ProtocolHeader(ProtocolMessage protoMsg, short messageLength) {
        this.protoMsg = protoMsg;
        this.messageLength = messageLength;
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
