package com.iota.iri.network.protocol;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Transaction;

import java.nio.ByteBuffer;

import org.hamcrest.core.IsInstanceOf;
import org.junit.Test;

import static org.junit.Assert.*;

public class ProtocolTest {

    @Test
    public void parsingHeaderWithIncompatibleProtocolVersionThrows() {
        try {
            ByteBuffer buf = (ByteBuffer) ByteBuffer.allocate(1).put((byte) 123).flip();
            Protocol.parseHeader(buf);
        } catch (Exception e) {
            assertThat(e, IsInstanceOf.instanceOf(IncompatibleProtocolVersionException.class));
            return;
        }
        fail("expected an exception to be thrown");
    }

    @Test
    public void parsingHeaderWithUnknownMessageTypeThrows() {
        try {
            ByteBuffer buf = ByteBuffer.allocate(2);
            buf.put((byte) 1);
            buf.put((byte) 123);
            buf.flip();
            Protocol.parseHeader(buf);
        } catch (Exception e) {
            assertThat(e, IsInstanceOf.instanceOf(UnknownMessageTypeException.class));
            return;
        }
        fail("expected an exception to be thrown");
    }

    @Test
    public void parsingHeaderWithTooBigAdvertisedLengthThrows() {
        try {
            ByteBuffer buf = ByteBuffer.allocate(4);
            buf.put((byte) 1);
            buf.put(ProtocolMessage.HANDSHAKE.getTypeID());
            buf.putShort((short) (ProtocolMessage.HANDSHAKE.getMaxLength() + 1));
            buf.flip();
            Protocol.parseHeader(buf);
        } catch (Exception e) {
            assertThat(e, IsInstanceOf.instanceOf(InvalidProtocolMessageLengthException.class));
            return;
        }
        fail("expected an exception to be thrown");
    }

    @Test
    public void parseHeader() {
        ProtocolHeader header = null;
        try {
            ByteBuffer buf = ByteBuffer.allocate(4);
            buf.put((byte) 1);
            buf.put(ProtocolMessage.HANDSHAKE.getTypeID());
            buf.putShort((ProtocolMessage.HANDSHAKE.getMaxLength()));
            buf.flip();
            header = Protocol.parseHeader(buf);
        } catch (Exception e) {
            fail("didn't expect any exceptions");
        }
        assertEquals(ProtocolMessage.HANDSHAKE.getTypeID(), header.getMessageType().getTypeID());
        assertEquals(ProtocolMessage.HANDSHAKE.getMaxLength(), header.getMessageLength());
        assertEquals(Protocol.PROTOCOL_VERSION, header.getVersion());
    }

    @Test
    public void createHandshakePacket() {
        char ownSourcePort = (char) 15600;
        long now = System.currentTimeMillis();
        byte[] byteEncodedCooAddress = Hash.NULL_HASH.bytes();
        ByteBuffer buf = Protocol.createHandshakePacket(ownSourcePort, byteEncodedCooAddress, (byte)1);
        assertEquals(1, buf.get());
        assertEquals(ProtocolMessage.HANDSHAKE.getTypeID(), buf.get());
        assertEquals(ProtocolMessage.HANDSHAKE.getMaxLength(), buf.getShort());
        assertEquals(ownSourcePort, buf.getChar());
        assertTrue(now <= buf.getLong());
        byte[] actualCooAddress = new byte[Protocol.BYTE_ENCODED_COO_ADDRESS_BYTES_LENGTH];
        buf.get(actualCooAddress);
        assertArrayEquals(byteEncodedCooAddress, actualCooAddress);
        assertEquals(1, buf.get());
    }

    private int nonEmptySigPartBytesCount = 1000;
    private int truncationBytesCount = Protocol.SIG_DATA_MAX_BYTES_LENGTH - nonEmptySigPartBytesCount;
    final private int sigFill = 3, restFill = 4, emptyFill = 0;

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
        assertEquals(Protocol.PROTOCOL_HEADER_BYTES_LENGTH + expectedMessageSize, buf.capacity());
        assertEquals(Protocol.PROTOCOL_VERSION, buf.get());
        assertEquals(ProtocolMessage.TRANSACTION_GOSSIP.getTypeID(), buf.get());
        assertEquals(expectedMessageSize, buf.getShort());
    }

    @Test
    public void expandTx() {
        int truncatedSize = 1000;
        byte[] truncatedTxData = new byte[truncatedSize];
        for (int i = 0; i < truncatedSize; i++) {
            truncatedTxData[i] = 3;
        }
        byte[] expandedTxData = new byte[Transaction.SIZE];
        Protocol.expandTx(truncatedTxData, expandedTxData);

        // stuff after signature message fragment should be intact
        for (int i = expandedTxData.length - 1; i >= Protocol.SIG_DATA_MAX_BYTES_LENGTH; i--) {
            assertEquals(3, expandedTxData[i]);
        }

        // bytes between truncated signature message fragment and rest should be 0s
        int expandedBytesCount = truncatedSize - Protocol.NON_SIG_TX_PART_BYTES_LENGTH;
        for (int i = Protocol.SIG_DATA_MAX_BYTES_LENGTH - 1; i > expandedBytesCount; i--) {
            assertEquals(0, expandedTxData[i]);
        }

        // origin signature message fragment should be intact
        for (int i = 0; i < Protocol.SIG_DATA_MAX_BYTES_LENGTH - expandedBytesCount; i++) {
            assertEquals(3, expandedTxData[i]);
        }
    }

    @Test
    public void truncateTx() {
        byte[] originTxData = constructTransactionBytes();
        byte[] truncatedTx = Protocol.truncateTx(originTxData);
        assertEquals(Transaction.SIZE - truncationBytesCount, truncatedTx.length);

        for (int i = 0; i < nonEmptySigPartBytesCount; i++) {
            assertEquals(sigFill, truncatedTx[i]);
        }
        for (int i = truncatedTx.length - 1; i > truncatedTx.length - Protocol.NON_SIG_TX_PART_BYTES_LENGTH; i--) {
            assertEquals(restFill, truncatedTx[i]);
        }
        for (int i = 0; i < truncatedTx.length; i++) {
            assertNotEquals(emptyFill, truncatedTx[i]);
        }
    }
}