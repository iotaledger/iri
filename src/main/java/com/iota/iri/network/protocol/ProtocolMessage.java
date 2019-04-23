package com.iota.iri.network.protocol;

/**
 * Defines the different message types supported by the protocol and their characteristics.
 */
public enum ProtocolMessage {
    /**
     * The message header sent in each message denoting the TLV fields and used protocol version.
     */
    HEADER((byte) 0, (short) Protocol.PROTOCOL_HEADER_BYTES_LENGTH, false),
    /**
     * The initial handshake packet sent over the wire up on a new neighbor connection.
     * Made up of:
     * - own server socket port (2 bytes)
     * - time at which the packet was sent (8 bytes)
     * - own used byte encoded coordinator address (49 bytes)
     * - own used MWM (1 byte)
     */
    HANDSHAKE((byte) 1, (short) 60, false),
    /**
     * The transaction payload + requested transaction hash gossipping packet. In reality most of this packets won't
     * take up their full 1604 bytes as the signature message fragment of the tx is truncated.
     */
    TRANSACTION_GOSSIP((byte) 2, (short) (Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES_LENGTH + Protocol.NON_SIG_TX_PART_BYTES_LENGTH
            + Protocol.SIG_DATA_MAX_BYTES_LENGTH), true);

    ProtocolMessage(byte typeID, short maxLength, boolean supportsDynamicLength) {
        this.typeID = typeID;
        this.maxLength = maxLength;
        this.supportsDynamicLength = supportsDynamicLength;
    }

    private static ProtocolMessage[] lookup = new ProtocolMessage[256];

    static {
        lookup[0] = ProtocolMessage.HEADER;
        lookup[1] = ProtocolMessage.HANDSHAKE;
        lookup[2] = ProtocolMessage.TRANSACTION_GOSSIP;
    }

    /**
     * Gets the {@link ProtocolMessage} corresponding to the given type id.
     * 
     * @param typeID the type id of the message
     * @return the {@link ProtocolMessage} corresponding to the given type id or null
     */
    public static ProtocolMessage fromTypeID(byte typeID) {
        if (typeID >= lookup.length) {
            return null;
        }
        return lookup[typeID];
    }

    private byte typeID;
    private short maxLength;
    private boolean supportsDynamicLength;

    /**
     * Gets the type id of the message.
     * 
     * @return the type id of the message
     */
    public byte getTypeID() {
        return typeID;
    }

    /**
     * Gets the maximum length of the message.
     * 
     * @return the maximum length of the message
     */
    public short getMaxLength() {
        return maxLength;
    }

    /**
     * Whether this message type supports dynamic length.
     * 
     * @return whether this message type supports dynamic length
     */
    public boolean supportsDynamicLength() {
        return supportsDynamicLength;
    }

}
