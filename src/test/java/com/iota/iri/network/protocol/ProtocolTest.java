package com.iota.iri.network.protocol;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Transaction;

import java.nio.ByteBuffer;

import org.hamcrest.core.IsInstanceOf;
import org.junit.Test;

import static org.junit.Assert.*;

public class ProtocolTest {

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
        assertTrue("timestamp in handshake packet should be same age or newer than timestamp", now <= buf.getLong());
        byte[] actualCooAddress = new byte[Handshake.BYTE_ENCODED_COO_ADDRESS_BYTES_LENGTH];
        buf.get(actualCooAddress);
        assertArrayEquals("should resolve to the correct coo address", byteEncodedCooAddress, actualCooAddress);
        assertEquals("mwm should be correct", 1, buf.get());
        byte[] supportedVersions = new byte[Protocol.SUPPORTED_PROTOCOL_VERSIONS.length];
        buf.get(supportedVersions);
        assertArrayEquals("should resolve to correct supported protocol versions", Protocol.SUPPORTED_PROTOCOL_VERSIONS,
                supportedVersions);
    }

    @Test
    public void createTransactionGossipPacket() {
        Transaction sourceTx = new Transaction();
        sourceTx.bytes = TransactionTestUtils.constructTransactionBytes();
        TransactionViewModel tvm = new TransactionViewModel(sourceTx, null);
        ByteBuffer buf = Protocol.createTransactionGossipPacket(tvm, Hash.NULL_HASH.bytes());
        final int expectedMessageSize = Transaction.SIZE - TransactionTestUtils.TRUNCATION_BYTES_COUNT
                + Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES_LENGTH;
        assertEquals("buffer should have the right capacity",
                Protocol.PROTOCOL_HEADER_BYTES_LENGTH + expectedMessageSize, buf.capacity());
        assertEquals("should be of type tx gossip message", ProtocolMessage.TRANSACTION_GOSSIP.getTypeID(), buf.get());
        assertEquals("should have correct message length", expectedMessageSize, buf.getShort());
    }
}