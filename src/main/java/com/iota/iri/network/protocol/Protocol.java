package com.iota.iri.network.protocol;

import com.iota.iri.controllers.TransactionViewModel;

import java.nio.ByteBuffer;
import java.util.HashMap;

/**
 * The IRI protocol uses a 4 bytes header denoting the version, type and length of a packet.
 */
public class Protocol {

    /**
     * The protocol version used by this node.
     */
    public final static byte PROTOCOL_VERSION = 1;
    /**
     * The amount of bytes dedicated for the protocol version in the packet header.
     */
    public final static byte HEADER_VERSION_BYTES = 1;
    /**
     * The amount of bytes dedicated for the message type in the packet header.
     */
    public final static byte HEADER_TLV_TYPE_BYTES = 1;
    /**
     * The amount of bytes dedicated for the message size denotation in the packet header.
     */
    public final static byte HEADER_TLV_LENGTH_BYTES = 2;
    /**
     * The amount of bytes making up the protocol packet header.
     */
    public final static byte PROTOCOL_HEADER_BYTES = HEADER_VERSION_BYTES + HEADER_TLV_LENGTH_BYTES
            + HEADER_TLV_TYPE_BYTES;

    /**
     * The amount of bytes used for the requested transaction hash.
     */
    public final static int GOSSIP_REQUESTED_TX_HASH_BYTES = 49;
    /**
     * The amount of bytes used for the coo address sent in a handshake packet.
     */
    public final static int BYTE_ENCODED_COO_ADDRESS_BYTES = 49;
    /**
     * The amount of bytes making up the non signature message fragment part of a transaction gossip payload.
     */
    public final static int NON_SIG_TX_PART_SIZE_BYTES = 292;
    /**
     * The max amount of bytes a signature message fragment is made up from.
     */
    public final static int SIG_DATA_MAX_SIZE_BYTES = 1312;

    /**
     * Defines the different message types supported by the protocol and their corresponding id.
     */
    public enum MessageType {
        HEADER((byte) 0), HANDSHAKE((byte) 1), TRANSACTION_GOSSIP((byte) 2);

        private byte type;

        MessageType(byte type) {
            this.type = type;
        }

        private static HashMap<Byte, MessageType> lookup = new HashMap<>();

        static {
            lookup.put((byte) 0, MessageType.HEADER);
            lookup.put((byte) 1, MessageType.HANDSHAKE);
            lookup.put((byte) 2, MessageType.TRANSACTION_GOSSIP);
        }

        public static MessageType fromValue(byte val) {
            return lookup.get(val);
        }

        public byte getValue() {
            return type;
        }
    }

    /**
     * Defines the message sizes corresponding to the message types. The sizes define the maximum amount of bytes for
     * the given message type.
     */
    public enum MessageSize {
        HEADER((short) PROTOCOL_HEADER_BYTES), HANDSHAKE((short) 59),
        // represents the max size of a tx payload + requested hash.
        // in reality most txs won't take up their full 1604 bytes as the
        // signature message fragment is truncated
        TRANSACTION_GOSSIP(
                (short) (GOSSIP_REQUESTED_TX_HASH_BYTES + NON_SIG_TX_PART_SIZE_BYTES + SIG_DATA_MAX_SIZE_BYTES));

        private static HashMap<MessageType, MessageSize> lookup = new HashMap<>();

        static {
            lookup.put(MessageType.HEADER, MessageSize.HEADER);
            lookup.put(MessageType.HANDSHAKE, MessageSize.HANDSHAKE);
            lookup.put(MessageType.TRANSACTION_GOSSIP, MessageSize.TRANSACTION_GOSSIP);
        }

        public static MessageSize fromType(MessageType messageType) {
            return lookup.get(messageType);
        }

        private short size;

        MessageSize(short size) {
            this.size = size;
        }

        public short getSize() {
            return this.size;
        }
    }

    /**
     * Parses the given buffer into a {@link ProtocolHeader}.
     * 
     * @param buf the buffer to parse
     * @return the parsed {@link ProtocolHeader}
     * @throws UnknownMessageTypeException          thrown when the advertised message type is unknown
     * @throws IncompatibleProtocolVersionException thrown when the protocol header contains an incompatible protocol
     *                                              version
     * @throws AdvertisedMessageSizeTooBigException thrown when the advertised message size exceeds the max message size
     *                                              for the given message type
     */
    public static ProtocolHeader parseHeader(ByteBuffer buf) throws UnknownMessageTypeException,
            IncompatibleProtocolVersionException, AdvertisedMessageSizeTooBigException {
        byte version = buf.get();
        if (version != PROTOCOL_VERSION) {
            throw new IncompatibleProtocolVersionException(String.format(
                    "got packet with incompatible protocol version v%d (current is v%d)", version, PROTOCOL_VERSION));
        }

        // extract type of message
        byte type = buf.get();
        MessageType messageType = MessageType.fromValue(type);
        if (messageType == null) {
            throw new UnknownMessageTypeException(String.format("got unknown message type in protocol: %d", type));
        }

        // extract size of message
        short advertisedMessageSize = buf.getShort();
        MessageSize messageSize = MessageSize.fromType(messageType);
        if (advertisedMessageSize > messageSize.getSize()) {
            throw new AdvertisedMessageSizeTooBigException(String.format(
                    "advertised size: %d bytes; max size: %d bytes", advertisedMessageSize, messageSize.getSize()));
        }

        return new ProtocolHeader(version, messageType, advertisedMessageSize);
    }

    /**
     * Creates a new handshake packet.
     * 
     * @param ownSourcePort the node's own server socket port number
     * @return a {@link ByteBuffer} containing the handshake packet
     */
    public static ByteBuffer createHandshakePacket(char ownSourcePort, byte[] ownByteEncodedCooAddress) {
        ByteBuffer buf = ByteBuffer.allocate(MessageSize.HEADER.getSize() + MessageSize.HANDSHAKE.getSize());
        addProtocolHeader(buf, MessageType.HANDSHAKE);
        buf.putChar(ownSourcePort);
        buf.putLong(System.currentTimeMillis());
        buf.put(ownByteEncodedCooAddress);
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
        final short payloadSizeBytes = (short) (truncatedTx.length + GOSSIP_REQUESTED_TX_HASH_BYTES);
        ByteBuffer buf = ByteBuffer.allocate(MessageSize.HEADER.getSize() + payloadSizeBytes);
        addProtocolHeader(buf, MessageType.TRANSACTION_GOSSIP, payloadSizeBytes);
        buf.put(truncatedTx);
        buf.put(requestedHash, 0, GOSSIP_REQUESTED_TX_HASH_BYTES);
        buf.flip();
        return buf;
    }

    /**
     * Adds the protocol header to the given {@link ByteBuffer}.
     * 
     * @param buf  the {@link ByteBuffer} to write into.
     * @param type the message type which will be sent
     */
    private static void addProtocolHeader(ByteBuffer buf, MessageType type) {
        addProtocolHeader(buf, type, MessageSize.fromType(type).getSize());
    }

    /**
     * Adds the protocol header to the given {@link ByteBuffer}.
     *
     * @param buf              the {@link ByteBuffer} to write into.
     * @param type             the message type which will be sent
     * @param payloadSizeBytes the message size
     */
    private static void addProtocolHeader(ByteBuffer buf, MessageType type, short payloadSizeBytes) {
        buf.put(PROTOCOL_VERSION);
        buf.put(type.getValue());
        buf.putShort(payloadSizeBytes);
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
        int numOfBytesOfSigMsgFragToExpand = Protocol.MessageSize.TRANSACTION_GOSSIP.getSize() - data.length;
        byte[] sigMsgFragPadding = new byte[numOfBytesOfSigMsgFragToExpand];
        int sigMsgFragBytesToCopy = data.length - Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES
                - Protocol.NON_SIG_TX_PART_SIZE_BYTES;

        // build up transaction payload. empty signature message fragment equals padding with 1312x 0 bytes
        System.arraycopy(data, 0, txDataBytes, 0, sigMsgFragBytesToCopy);
        System.arraycopy(sigMsgFragPadding, 0, txDataBytes, sigMsgFragBytesToCopy, sigMsgFragPadding.length);
        System.arraycopy(data, sigMsgFragBytesToCopy, txDataBytes, Protocol.SIG_DATA_MAX_SIZE_BYTES,
                Protocol.NON_SIG_TX_PART_SIZE_BYTES);
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
        for (int i = SIG_DATA_MAX_SIZE_BYTES - 1; i >= 0; i--) {
            if (txBytes[i] != 0) {
                break;
            }
            bytesToTruncate++;
        }
        // allocate space for truncated tx
        byte[] truncatedTx = new byte[SIG_DATA_MAX_SIZE_BYTES - bytesToTruncate + NON_SIG_TX_PART_SIZE_BYTES];
        System.arraycopy(txBytes, 0, truncatedTx, 0, SIG_DATA_MAX_SIZE_BYTES - bytesToTruncate);
        System.arraycopy(txBytes, SIG_DATA_MAX_SIZE_BYTES, truncatedTx, SIG_DATA_MAX_SIZE_BYTES - bytesToTruncate,
                NON_SIG_TX_PART_SIZE_BYTES);
        return truncatedTx;
    }

}
