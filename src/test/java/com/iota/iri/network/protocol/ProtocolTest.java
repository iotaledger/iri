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
    public void parsingHeaderWithTooBigAdvertisedSizeThrows() {
        try {
            ByteBuffer buf = ByteBuffer.allocate(4);
            buf.put((byte) 1);
            buf.put(Protocol.MessageType.HANDSHAKE.getValue());
            buf.putShort((short) (Protocol.MessageSize.HANDSHAKE.getSize() + 1));
            buf.flip();
            Protocol.parseHeader(buf);
        } catch (Exception e) {
            assertThat(e, IsInstanceOf.instanceOf(AdvertisedMessageSizeTooBigException.class));
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
            buf.put(Protocol.MessageType.HANDSHAKE.getValue());
            buf.putShort((Protocol.MessageSize.HANDSHAKE.getSize()));
            buf.flip();
            header = Protocol.parseHeader(buf);
        } catch (Exception e) {
            fail("didn't expect any exceptions");
        }
        assertEquals(Protocol.MessageType.HANDSHAKE.getValue(), header.getMessageType().getValue());
        assertEquals(Protocol.MessageSize.HANDSHAKE.getSize(), header.getMessageSize());
        assertEquals(Protocol.PROTOCOL_VERSION, header.getVersion());
    }

    @Test
    public void createHandshakePacket() {
        char ownSourcePort = (char) 15600;
        long now = System.currentTimeMillis();
        ByteBuffer buf = Protocol.createHandshakePacket(ownSourcePort);
        assertEquals(1, buf.get());
        assertEquals(Protocol.MessageType.HANDSHAKE.getValue(), buf.get());
        assertEquals(Protocol.MessageSize.HANDSHAKE.getSize(), buf.getShort());
        assertEquals(ownSourcePort, buf.getChar());
        assertTrue(now <= buf.getLong());
    }

    private int nonEmptySigPartBytesCount = 1000;
    private int truncationBytesCount = Protocol.SIG_DATA_MAX_SIZE_BYTES - nonEmptySigPartBytesCount;
    final private int sigFill = 3, restFill = 4, emptyFill = 0;

    private byte[] constructTransactionBytes() {
        byte[] originTxData = new byte[Transaction.SIZE];
        for (int i = 0; i < nonEmptySigPartBytesCount; i++) {
            originTxData[i] = sigFill;
        }
        for (int i = nonEmptySigPartBytesCount; i < Protocol.SIG_DATA_MAX_SIZE_BYTES; i++) {
            originTxData[i] = emptyFill;
        }
        for (int i = Protocol.SIG_DATA_MAX_SIZE_BYTES; i < Transaction.SIZE; i++) {
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
        final int expectedMessageSize = Transaction.SIZE - truncationBytesCount + Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES;
        assertEquals(Protocol.PROTOCOL_HEADER_BYTES + expectedMessageSize, buf.capacity());
        assertEquals(Protocol.PROTOCOL_VERSION, buf.get());
        assertEquals(Protocol.MessageType.TRANSACTION_GOSSIP.getValue(), buf.get());
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
        for (int i = expandedTxData.length - 1; i >= Protocol.SIG_DATA_MAX_SIZE_BYTES; i--) {
            assertEquals(3, expandedTxData[i]);
        }

        // bytes between truncated signature message fragment and rest should be 0s
        int expandedBytesCount = truncatedSize - Protocol.NON_SIG_TX_PART_SIZE_BYTES;
        for (int i = Protocol.SIG_DATA_MAX_SIZE_BYTES - 1; i > expandedBytesCount; i--) {
            assertEquals(0, expandedTxData[i]);
        }

        // origin signature message fragment should be intact
        for (int i = 0; i < Protocol.SIG_DATA_MAX_SIZE_BYTES - expandedBytesCount; i++) {
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
        for (int i = truncatedTx.length - 1; i > truncatedTx.length - Protocol.NON_SIG_TX_PART_SIZE_BYTES; i--) {
            assertEquals(restFill, truncatedTx[i]);
        }
        for (int i = 0; i < truncatedTx.length; i++) {
            assertNotEquals(emptyFill, truncatedTx[i]);
        }
    }
}