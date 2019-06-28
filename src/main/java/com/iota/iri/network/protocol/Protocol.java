package com.iota.iri.network.protocol;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.persistables.Transaction;

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
     * <p>
     * The supported protocol versions by this node. Bitmasks are used to denote what protocol version this node
     * supports in its implementation. The LSB acts as a starting point. Up to 32 bytes are supported in the handshake
     * packet, limiting the amount of supported denoted protocol versions to 256.
     * </p>
     * <p>
     * Examples:
     * </p>
     * <ul>
     * <li>[00000001] denotes that this node supports protocol version 1.</li>
     * <li>[00000111] denotes that this node supports protocol versions 1, 2 and 3.</li>
     * <li>[01101110] denotes that this node supports protocol versions 2, 3, 4, 6 and 7.</li>
     * <li>[01101110, 01010001] denotes that this node supports protocol versions 2, 3, 4, 6, 7, 9, 13 and 15.</li>
     * <li>[01101110, 01010001, 00010001] denotes that this node supports protocol versions 2, 3, 4, 6, 7, 9, 13, 15,
     * 17 and 21.</li>
     * </ul>
     */
    public final static byte[] SUPPORTED_PROTOCOL_VERSIONS = {
            /* supports protocol version(s): 1 */
            (byte) 0b00000001,
    };
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
    public static void addProtocolHeader(ByteBuffer buf, ProtocolMessage protoMsg) {
        addProtocolHeader(buf, protoMsg, protoMsg.getMaxLength());
    }

    /**
     * Adds the protocol header to the given {@link ByteBuffer}.
     *
     * @param buf                the {@link ByteBuffer} to write into.
     * @param protoMsg           the message type which will be sent
     * @param payloadLengthBytes the message length
     */
    public static void addProtocolHeader(ByteBuffer buf, ProtocolMessage protoMsg, short payloadLengthBytes) {
        buf.put(protoMsg.getTypeID());
        buf.putShort(payloadLengthBytes);
    }

    /**
     * Expands a truncated bytes encoded transaction payload.
     * 
     * @param data the source data
     */
    public static byte[] expandTx(byte[] data) {
        byte[] txDataBytes = new byte[Transaction.SIZE];
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
        return txDataBytes;
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

    /**
     * Copies the requested transaction hash from the given source data byte array into the given destination byte
     * array.
     * 
     * @param source the transaction gossip packet data
     */
    public static byte[] extractRequestedTxHash(byte[] source) {
        byte[] reqHashBytes = new byte[Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES_LENGTH];
        System.arraycopy(source, source.length - Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES_LENGTH, reqHashBytes, 0,
                Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES_LENGTH);
        return reqHashBytes;
    }

}
