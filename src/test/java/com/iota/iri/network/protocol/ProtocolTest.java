package com.iota.iri.network.protocol;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Transaction;

import java.nio.ByteBuffer;

import org.hamcrest.core.IsInstanceOf;
import org.junit.Test;

import static org.junit.Assert.*;

public class ProtocolTest {

    private int nonEmptySigPartBytesCount = 1000;
    private int truncationBytesCount = Protocol.SIG_DATA_MAX_BYTES_LENGTH - nonEmptySigPartBytesCount;
    final private int sigFill = 3, restFill = 4, emptyFill = 0;

    @Test
    public void parsingHeaderWithUnknownMessageTypeThrows() {
        try {
            ByteBuffer buf = ByteBuffer.allocate(3);
            buf.put((byte) 123);
            buf.putShort((short) 500);
            buf.flip();
            Protocol.parseHeader(buf);
        } catch (Exception e) {
            assertThat("because the message is unknown", e, IsInstanceOf.instanceOf(UnknownMessageTypeException.class));
            return;
        }
        fail("expected an exception to be thrown");
    }

    @Test
    public void parsingHeaderWithTooBigAdvertisedLengthThrows() {
        try {
            ByteBuffer buf = ByteBuffer.allocate(3);
            buf.put(ProtocolMessage.HANDSHAKE.getTypeID());
            buf.putShort((short) (ProtocolMessage.HANDSHAKE.getMaxLength() + 1));
            buf.flip();
            Protocol.parseHeader(buf);
        } catch (Exception e) {
            assertThat("because the length is invalid for the given message", e,
                    IsInstanceOf.instanceOf(InvalidProtocolMessageLengthException.class));
            return;
        }
        fail("expected an exception to be thrown");
    }

    @Test
    public void parseHeader() {
        ProtocolHeader header = null;
        try {
            ByteBuffer buf = ByteBuffer.allocate(3);
            buf.put(ProtocolMessage.HANDSHAKE.getTypeID());
            buf.putShort((ProtocolMessage.HANDSHAKE.getMaxLength()));
            buf.flip();
            header = Protocol.parseHeader(buf);
        } catch (Exception e) {
            fail("didn't expect any exceptions");
        }
        assertEquals("should be of type handshake message", ProtocolMessage.HANDSHAKE.getTypeID(),
                header.getMessageType().getTypeID());
        assertEquals("length should be of handshake message length", ProtocolMessage.HANDSHAKE.getMaxLength(),
                header.getMessageLength());
    }

    @Test
    public void createHandshakePacket() {
        char ownSourcePort = (char) 15600;
        long now = System.currentTimeMillis();
        byte[] byteEncodedCooAddress = Hash.NULL_HASH.bytes();
        ByteBuffer buf = Handshake.createHandshakePacket(ownSourcePort, byteEncodedCooAddress, (byte) 1);
        assertEquals("should be of type handshake message", ProtocolMessage.HANDSHAKE.getTypeID(), buf.get());
        int maxLength = ProtocolMessage.HANDSHAKE.getMaxLength();
        assertEquals("should have correct length",
                (maxLength - (maxLength - 60) + Protocol.SUPPORTED_PROTOCOL_VERSIONS.length), buf.getShort());
        assertEquals("should resolve to the correct source port", ownSourcePort, buf.getChar());
        assertTrue("timestamp in handshake packet should be same age or newer than timestamp",now <= buf.getLong());
        byte[] actualCooAddress = new byte[Handshake.BYTE_ENCODED_COO_ADDRESS_BYTES_LENGTH];
        buf.get(actualCooAddress);
        assertArrayEquals("should resolve to the correct coo address", byteEncodedCooAddress, actualCooAddress);
        assertEquals("mwm should be correct", 1, buf.get());
        byte[] supportedVersions = new byte[Protocol.SUPPORTED_PROTOCOL_VERSIONS.length];
        buf.get(supportedVersions);
        assertArrayEquals("should resolve to correct supported protocol versions", Protocol.SUPPORTED_PROTOCOL_VERSIONS,
                supportedVersions);
    }

    private byte[] constructTransactionBytes() {
        byte[] originTxData = new byte[Transaction.SIZE];
        for (int i = 0; i < nonEmptySigPartBytesCount; i++) {
            originTxData[i] = sigFill;
        }
        for (int i = nonEmptySigPartBytesCount; i < Protocol.SIG_DATA_MAX_BYTES_LENGTH; i++) {
            originTxData[i] = emptyFill;
        }
        for (int i = Protocol.SIG_DATA_MAX_BYTES_LENGTH; i < Transaction.SIZE; i++) {
            originTxData[i] = restFill;
        }
        return originTxData;
    }

    @Test
    public void createTransactionGossipPacket() {
        Transaction sourceTx = new Transaction();
        sourceTx.bytes = constructTransactionBytes();
        TransactionViewModel tvm = new TransactionViewModel(sourceTx, null);
        ByteBuffer buf = Protocol.createTransactionGossipPacket(tvm, Hash.NULL_HASH.bytes());
        final int expectedMessageSize = Transaction.SIZE - truncationBytesCount
                + Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES_LENGTH;
        assertEquals("buffer should have the right capacity",
                Protocol.PROTOCOL_HEADER_BYTES_LENGTH + expectedMessageSize, buf.capacity());
        assertEquals("should be of type tx gossip message", ProtocolMessage.TRANSACTION_GOSSIP.getTypeID(), buf.get());
        assertEquals("should have correct message length", expectedMessageSize, buf.getShort());
    }

    @Test
    public void expandTx() {
        int truncatedSize = 1000;
        byte[] truncatedTxData = new byte[truncatedSize];
        for (int i = 0; i < truncatedSize; i++) {
            truncatedTxData[i] = 3;
        }
        byte[] expandedTxData = Protocol.expandTx(truncatedTxData);

        // stuff after signature message fragment should be intact
        for (int i = expandedTxData.length - 1; i >= Protocol.SIG_DATA_MAX_BYTES_LENGTH; i--) {
            assertEquals("non sig data should be intact", 3, expandedTxData[i]);
        }

        // bytes between truncated signature message fragment and rest should be 0s
        int expandedBytesCount = truncatedSize - Protocol.NON_SIG_TX_PART_BYTES_LENGTH;
        for (int i = Protocol.SIG_DATA_MAX_BYTES_LENGTH - 1; i > expandedBytesCount; i--) {
            assertEquals("expanded sig frag should be filled with zero at the right positions", 0, expandedTxData[i]);
        }

        // origin signature message fragment should be intact
        for (int i = 0; i < Protocol.SIG_DATA_MAX_BYTES_LENGTH - expandedBytesCount; i++) {
            assertEquals("origin sig frag should be intact", 3, expandedTxData[i]);
        }
    }

    @Test
    public void truncateTx() {
        byte[] originTxData = constructTransactionBytes();
        byte[] truncatedTx = Protocol.truncateTx(originTxData);
        assertEquals("should have the correct size after truncation", Transaction.SIZE - truncationBytesCount,
                truncatedTx.length);

        for (int i = 0; i < nonEmptySigPartBytesCount; i++) {
            assertEquals("non empty sig frag part should be intact", sigFill, truncatedTx[i]);
        }
        for (int i = truncatedTx.length - 1; i > truncatedTx.length - Protocol.NON_SIG_TX_PART_BYTES_LENGTH; i--) {
            assertEquals("non sig frag part should be intact", restFill, truncatedTx[i]);
        }
        for (int i = 0; i < truncatedTx.length; i++) {
            assertNotEquals(
                    "truncated tx should not have zero bytes (given the non sig frag part not containing zero bytes)",
                    emptyFill, truncatedTx[i]);
        }
    }
}