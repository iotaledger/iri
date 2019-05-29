package com.iota.iri.network.protocol;

import com.iota.iri.controllers.TransactionViewModel;

import java.nio.ByteBuffer;

/**
 * The IRI protocol uses a 4 bytes header denoting the version, type and length of a packet.
 */
public class Protocol {

    /**
     * The protocol version used by this node.
     */
    public final static byte PROTOCOL_VERSION = 1;
    /**
     * The supported protocol versions by this node.
     */
    public final static byte[] SUPPORTED_PROTOCOL_VERSIONS = { (byte) 0b10000000 };
    /**
     * The amount of bytes dedicated for the message type in the packet header.
     */
    public final static byte HEADER_TLV_TYPE_BYTES_LENGTH = 1;
    /**
     * The amount of bytes dedicated for the message length denotation in the packet header.
     */
    public final static byte HEADER_TLV_LENGTH_BYTES_LENGTH = 2;
    /**
     * The amount of bytes making up the protocol packet header.
     */
    public final static byte PROTOCOL_HEADER_BYTES_LENGTH = HEADER_TLV_LENGTH_BYTES_LENGTH
            + HEADER_TLV_TYPE_BYTES_LENGTH;

    /**
     * The amount of bytes used for the requested transaction hash.
     */
    public final static int GOSSIP_REQUESTED_TX_HASH_BYTES_LENGTH = 49;
    /**
     * The amount of bytes used for the coo address sent in a handshake packet.
     */
    public final static int BYTE_ENCODED_COO_ADDRESS_BYTES_LENGTH = 49;
    /**
     * The amount of bytes making up the non signature message fragment part of a transaction gossip payload.
     */
    public final static int NON_SIG_TX_PART_BYTES_LENGTH = 292;
    /**
     * The max amount of bytes a signature message fragment is made up from.
     */
    public final static int SIG_DATA_MAX_BYTES_LENGTH = 1312;

    /**
     * Parses the given buffer into a {@link ProtocolHeader}.
     * 
     * @param buf the buffer to parse
     * @return the parsed {@link ProtocolHeader}
     * @throws UnknownMessageTypeException           thrown when the advertised message type is unknown
     * @throws InvalidProtocolMessageLengthException thrown when the advertised message length is invalid
     */
    public static ProtocolHeader parseHeader(ByteBuffer buf)
            throws UnknownMessageTypeException, InvalidProtocolMessageLengthException {

        // extract type of message
        byte type = buf.get();
        ProtocolMessage protoMsg = ProtocolMessage.fromTypeID(type);
        if (protoMsg == null) {
            throw new UnknownMessageTypeException(String.format("got unknown message type in protocol: %d", type));
        }

        // extract length of message
        short advertisedMsgLength = buf.getShort();
        if ((advertisedMsgLength > protoMsg.getMaxLength())
                || (!protoMsg.supportsDynamicLength() && advertisedMsgLength < protoMsg.getMaxLength())) {
            throw new InvalidProtocolMessageLengthException(String.format(
                    "advertised length: %d bytes; max length: %d bytes", advertisedMsgLength, protoMsg.getMaxLength()));
        }

        return new ProtocolHeader(protoMsg, advertisedMsgLength);
    }

    /**
     * Creates a new handshake packet.
     * 
     * @param ownSourcePort the node's own server socket port number
     * @return a {@link ByteBuffer} containing the handshake packet
     */
    public static ByteBuffer createHandshakePacket(char ownSourcePort, byte[] ownByteEncodedCooAddress,
            byte ownUsedMWM) {
        short maxLength = ProtocolMessage.HANDSHAKE.getMaxLength();
        final short payloadLengthBytes = (short) (maxLength - (maxLength - 60) + SUPPORTED_PROTOCOL_VERSIONS.length);
        ByteBuffer buf = ByteBuffer.allocate(ProtocolMessage.HEADER.getMaxLength() + payloadLengthBytes);
        addProtocolHeader(buf, ProtocolMessage.HANDSHAKE, payloadLengthBytes);
        buf.putChar(ownSourcePort);
        buf.putLong(System.currentTimeMillis());
        buf.put(ownByteEncodedCooAddress);
        buf.put(ownUsedMWM);
        buf.put(SUPPORTED_PROTOCOL_VERSIONS);
        buf.flip();
        return buf;
    }

    /**
     * Creates a new transaction gossip packet.
     * 
     * @param tvm           The transaction to add into the packet
     * @param requestedHash The hash of the requested transaction
     * @return a {@link ByteBuffer} containing the transaction gossip packet.
     */
    public static ByteBuffer createTransactionGossipPacket(TransactionViewModel tvm, byte[] requestedHash) {
        byte[] truncatedTx = truncateTx(tvm.getBytes());
        final short payloadLengthBytes = (short) (truncatedTx.length + GOSSIP_REQUESTED_TX_HASH_BYTES_LENGTH);
        ByteBuffer buf = ByteBuffer.allocate(ProtocolMessage.HEADER.getMaxLength() + payloadLengthBytes);
        addProtocolHeader(buf, ProtocolMessage.TRANSACTION_GOSSIP, payloadLengthBytes);
        buf.put(truncatedTx);
        buf.put(requestedHash, 0, GOSSIP_REQUESTED_TX_HASH_BYTES_LENGTH);
        buf.flip();
        return buf;
    }

    /**
     * Adds the protocol header to the given {@link ByteBuffer}.
     * 
     * @param buf      the {@link ByteBuffer} to write into.
     * @param protoMsg the message type which will be sent
     */
    private static void addProtocolHeader(ByteBuffer buf, ProtocolMessage protoMsg) {
        addProtocolHeader(buf, protoMsg, protoMsg.getMaxLength());
    }

    /**
     * Adds the protocol header to the given {@link ByteBuffer}.
     *
     * @param buf                the {@link ByteBuffer} to write into.
     * @param protoMsg           the message type which will be sent
     * @param payloadLengthBytes the message length
     */
    private static void addProtocolHeader(ByteBuffer buf, ProtocolMessage protoMsg, short payloadLengthBytes) {
        buf.put(protoMsg.getTypeID());
        buf.putShort(payloadLengthBytes);
    }

    /**
     * Expands a truncated bytes encoded transaction payload.
     * 
     * @param data        the source data
     * @param txDataBytes the array to expand the transaction into
     */
    public static void expandTx(byte[] data, byte[] txDataBytes) {
        // we need to expand the tx data (signature message fragment) as
        // it could have been truncated for transmission
        int numOfBytesOfSigMsgFragToExpand = ProtocolMessage.TRANSACTION_GOSSIP.getMaxLength() - data.length;
        byte[] sigMsgFragPadding = new byte[numOfBytesOfSigMsgFragToExpand];
        int sigMsgFragBytesToCopy = data.length - Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES_LENGTH
                - Protocol.NON_SIG_TX_PART_BYTES_LENGTH;

        // build up transaction payload. empty signature message fragment equals padding with 1312x 0 bytes
        System.arraycopy(data, 0, txDataBytes, 0, sigMsgFragBytesToCopy);
        System.arraycopy(sigMsgFragPadding, 0, txDataBytes, sigMsgFragBytesToCopy, sigMsgFragPadding.length);
        System.arraycopy(data, sigMsgFragBytesToCopy, txDataBytes, Protocol.SIG_DATA_MAX_BYTES_LENGTH,
                Protocol.NON_SIG_TX_PART_BYTES_LENGTH);
    }

    /**
     * Truncates the given bytes encoded transaction data.
     * 
     * @param txBytes the transaction bytes to truncate
     * @return an array containing the truncated transaction data
     */
    public static byte[] truncateTx(byte[] txBytes) {
        // check how many bytes from the signature can be truncated
        int bytesToTruncate = 0;
        for (int i = SIG_DATA_MAX_BYTES_LENGTH - 1; i >= 0; i--) {
            if (txBytes[i] != 0) {
                break;
            }
            bytesToTruncate++;
        }
        // allocate space for truncated tx
        byte[] truncatedTx = new byte[SIG_DATA_MAX_BYTES_LENGTH - bytesToTruncate + NON_SIG_TX_PART_BYTES_LENGTH];
        System.arraycopy(txBytes, 0, truncatedTx, 0, SIG_DATA_MAX_BYTES_LENGTH - bytesToTruncate);
        System.arraycopy(txBytes, SIG_DATA_MAX_BYTES_LENGTH, truncatedTx, SIG_DATA_MAX_BYTES_LENGTH - bytesToTruncate,
                NON_SIG_TX_PART_BYTES_LENGTH);
        return truncatedTx;
    }

}
