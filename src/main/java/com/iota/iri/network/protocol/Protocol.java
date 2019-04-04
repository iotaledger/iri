package com.iota.iri.network.protocol;

import com.iota.iri.controllers.TransactionViewModel;

import java.nio.ByteBuffer;
import java.util.HashMap;

/**
 * The IRI protocol uses a 4 bytes header denoting the version, type
 * and length of a packet.
 */
public class Protocol {

    public final static byte PROTOCOL_VERSION = 1;
    public final static byte HEADER_VERSION_BYTES = 1;
    public final static byte HEADER_TLV_TYPE_BYTES = 1;
    public final static byte HEADER_TLV_LENGTH_BYTES = 2;
    public final static byte PROTOCOL_HEADER_BYTES = HEADER_VERSION_BYTES + HEADER_TLV_LENGTH_BYTES + HEADER_TLV_TYPE_BYTES;

    // actual transaction hash would be 49 bytes, but assuming that the last N
    // chars are 9s, it probably has been reduced to 46 bytes.
    public final static int GOSSIP_REQUESTED_TX_HASH_BYTES = 49;
    public final static int NON_SIG_TX_PART_SIZE_BYTES = 292;
    public final static int SIG_DATA_MAX_SIZE_BYTES = 1312;

    public enum MessageType {
        HEADER((byte) 0),
        HANDSHAKE((byte) 1),
        TRANSACTION_GOSSIP((byte) 2);

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

    public enum MessageSize {
        HEADER((short) PROTOCOL_HEADER_BYTES),
        HANDSHAKE((short) 10),
        // represents the max size of a tx payload + requested hash.
        // in reality most txs won't take up their full 1604 bytes as the
        // signature message fragment is truncated
        TRANSACTION_GOSSIP((short) (GOSSIP_REQUESTED_TX_HASH_BYTES + NON_SIG_TX_PART_SIZE_BYTES + SIG_DATA_MAX_SIZE_BYTES));

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

    public static ProtocolHeader parseHeader(ByteBuffer buf) throws UnknownMessageTypeException, IncompatibleProtocolVersionException, AdvertisedMessageSizeTooBigException {
        byte version = buf.get();
        if (version != PROTOCOL_VERSION) {
            throw new IncompatibleProtocolVersionException(String.format("got packet with incompatible protocol version v%d (current is v%d)", version, PROTOCOL_VERSION));
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
            throw new AdvertisedMessageSizeTooBigException(String.format("advertised size: %d bytes; max size: %d bytes", advertisedMessageSize, messageSize.getSize()));
        }

        return new ProtocolHeader(version, messageType, advertisedMessageSize);
    }

    public static ByteBuffer createHandshakePacket(char ownSourcePort) {
        ByteBuffer buf = ByteBuffer.allocate(MessageSize.HEADER.getSize() + MessageSize.HANDSHAKE.getSize());
        addProtocolHeader(buf, MessageType.HANDSHAKE);
        buf.putChar(ownSourcePort);
        buf.putLong(System.currentTimeMillis());
        buf.flip();
        return buf;
    }

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

    private static void addProtocolHeader(ByteBuffer buf, MessageType type) {
        addProtocolHeader(buf, type, MessageSize.fromType(type).getSize());
    }

    private static void addProtocolHeader(ByteBuffer buf, MessageType type, short payloadSizeBytes) {
        buf.put(PROTOCOL_VERSION);
        buf.put(type.getValue());
        buf.putShort(payloadSizeBytes);
    }

    public static void expandTx(byte[] data, byte[] txDataBytes) {
        // we need to expand the tx data (signature message fragment) as
        // it could have been truncated for transmission
        int numOfBytesOfSigMsgFragToExpand = Protocol.MessageSize.TRANSACTION_GOSSIP.getSize() - data.length;
        byte[] sigMsgFragPadding = new byte[numOfBytesOfSigMsgFragToExpand];
        int sigMsgFragBytesToCopy = data.length - Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES - Protocol.NON_SIG_TX_PART_SIZE_BYTES;

        // build up transaction payload. empty signature message fragment equals padding with 1312x 0 bytes
        System.arraycopy(data, 0, txDataBytes, 0, sigMsgFragBytesToCopy);
        System.arraycopy(sigMsgFragPadding, 0, txDataBytes, sigMsgFragBytesToCopy, sigMsgFragPadding.length);
        System.arraycopy(data, sigMsgFragBytesToCopy, txDataBytes, Protocol.SIG_DATA_MAX_SIZE_BYTES, Protocol.NON_SIG_TX_PART_SIZE_BYTES);
    }

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
        System.arraycopy(txBytes, SIG_DATA_MAX_SIZE_BYTES, truncatedTx, SIG_DATA_MAX_SIZE_BYTES - bytesToTruncate, NON_SIG_TX_PART_SIZE_BYTES);
        return truncatedTx;
    }

}
