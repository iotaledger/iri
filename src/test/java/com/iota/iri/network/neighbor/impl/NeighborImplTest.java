package com.iota.iri.network.neighbor.impl;

import com.iota.iri.model.Hash;
import com.iota.iri.network.FakeChannel;
import com.iota.iri.network.FakeSelectionKey;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.network.neighbor.NeighborState;
import com.iota.iri.network.pipeline.TransactionProcessingPipeline;
import com.iota.iri.network.protocol.Handshake;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

import com.iota.iri.network.protocol.ProtocolMessage;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class NeighborImplTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Selector selector;

    @Mock
    private TransactionProcessingPipeline pipeline;

    private final static String localAddr = "127.0.0.1";
    private final static char serverSocketPort = 15600;
    private final static int txMessageMaxSize = ProtocolMessage.TRANSACTION_GOSSIP.getMaxLength();

    private ByteBuffer createEmptyTxPacket() {
        ByteBuffer buf = ByteBuffer.allocate(3 + txMessageMaxSize);
        buf.put(ProtocolMessage.TRANSACTION_GOSSIP.getTypeID());
        buf.putShort(ProtocolMessage.TRANSACTION_GOSSIP.getMaxLength());
        buf.put(new byte[txMessageMaxSize]);
        buf.flip();
        return buf;
    }

    @Test
    public void handshakingWorks() {
        Neighbor neighbor = new NeighborImpl<>(selector, new FakeChannel() {

            // fake having a handshaking packet in the socket
            private ByteBuffer handshakePacket = Handshake.createHandshakePacket(serverSocketPort,
                    Hash.NULL_HASH.bytes(), (byte) 1);

            @Override
            public int read(ByteBuffer dst) {
                while (dst.hasRemaining()) {
                    dst.put(handshakePacket.get());
                }
                return 0;
            }
        }, localAddr, Neighbor.UNKNOWN_REMOTE_SERVER_SOCKET_PORT, pipeline);

        assertEquals("should be in handshaking state", NeighborState.HANDSHAKING, neighbor.getState());

        try {
            Handshake handshake = neighbor.handshake();
            assertEquals("should be ok after full handshake", Handshake.State.OK, handshake.getState());
            assertEquals("should have gotten the correct port from the handshake", serverSocketPort,
                    handshake.getServerSocketPort());
        } catch (IOException e) {
            fail("didnt expect an exception");
        }
    }

    @Test
    public void handshakeWithWrongPacketPutsItIntoFailedState() {
        ByteBuffer wrongPacket = createEmptyTxPacket();
        Neighbor neighbor = new NeighborImpl<>(selector, new FakeChannel() {

            @Override
            public int read(ByteBuffer dst) {
                while (dst.hasRemaining()) {
                    dst.put(wrongPacket.get());
                }
                return 0;
            }
        }, localAddr, Neighbor.UNKNOWN_REMOTE_SERVER_SOCKET_PORT, pipeline);

        assertEquals("should be in handshaking state", NeighborState.HANDSHAKING, neighbor.getState());

        try {
            Handshake handshake = neighbor.handshake();
            assertEquals("the handshake should have failed", Handshake.State.FAILED, handshake.getState());
        } catch (IOException e) {
            fail("didnt expect an exception");
        }
    }

    @Test
    public void readingTransactionFullyPutsItInThePipeline() {
        ByteBuffer emptyTxPacket = createEmptyTxPacket();
        Neighbor neighbor = new NeighborImpl<>(selector, new FakeChannel() {

            @Override
            public int read(ByteBuffer dst) {
                int bytesWritten = 0;
                while (dst.hasRemaining()) {
                    dst.put(emptyTxPacket.get());
                    bytesWritten++;
                }
                return bytesWritten;
            }
        }, localAddr, serverSocketPort, pipeline);

        // set the neighbor as ready for other messages
        neighbor.setState(NeighborState.READY_FOR_MESSAGES);

        try {
            assertEquals("should read the entire message", txMessageMaxSize, neighbor.read());
        } catch (IOException e) {
            fail("didn't expect an exception");
        }

        ByteBuffer expected = ByteBuffer.allocate(txMessageMaxSize);
        Mockito.verify(pipeline).process(neighbor, expected);
    }

    @Test
    public void writeWithAMessageInTheSendQueueWritesItToTheChannel() {
        Neighbor neighbor = new NeighborImpl<>(selector, new FakeChannel() {

            @Override
            public int write(ByteBuffer buf) {
                int bytesWritten = 0;
                while (buf.hasRemaining()) {
                    buf.get();
                    bytesWritten++;
                }
                return bytesWritten;
            }
        }, localAddr, serverSocketPort, pipeline);

        neighbor.send(createEmptyTxPacket());

        try {
            assertEquals("should have written the entire packet", createEmptyTxPacket().capacity(), neighbor.write());
        } catch (IOException e) {
            fail("didn't expect an exception");
        }
    }

    @Test
    public void writeWithNoMessageInTheSendQueueReturnsZero() {
        Neighbor neighbor = new NeighborImpl<>(selector, null, localAddr, serverSocketPort, pipeline);
        try {
            assertEquals("should return zero when no message has to be sent", 0, neighbor.write());
        } catch (IOException e) {
            fail("didn't expect an exception");
        }
    }

    @Test
    public void aSendWhileWriteInterestIsDisabledActivatesItAgain() {
        SelectionKey fakeSelectionKey = new FakeSelectionKey() {

            private int ops = SelectionKey.OP_READ;

            @Override
            public boolean isValid() {
                return true;
            }

            @Override
            public int interestOps() {
                return ops;
            }

            @Override
            public SelectionKey interestOps(int ops) {
                this.ops = ops;
                return null;
            }
        };
        Neighbor neighbor = new NeighborImpl<>(selector, new FakeChannel() {

            @Override
            public SelectionKey keyFor(Selector sel) {
                return fakeSelectionKey;
            }
        }, localAddr, serverSocketPort, pipeline);
        neighbor.send(createEmptyTxPacket());

        Mockito.verify(selector).wakeup();
        assertEquals("should be interested in read and write readiness", SelectionKey.OP_READ | SelectionKey.OP_WRITE,
                fakeSelectionKey.interestOps());
    }

    @Test
    public void markingTheNeighborForDisconnectWillNeverMakeItReadyForMessagesAgain() {
        Neighbor neighbor = new NeighborImpl<>(selector, null, localAddr, serverSocketPort, pipeline);
        neighbor.setState(NeighborState.MARKED_FOR_DISCONNECT);
        neighbor.setState(NeighborState.READY_FOR_MESSAGES);
        assertEquals("should be marked for disconnect", NeighborState.MARKED_FOR_DISCONNECT, neighbor.getState());
    }
}