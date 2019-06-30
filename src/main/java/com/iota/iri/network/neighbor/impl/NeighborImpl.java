package com.iota.iri.network.neighbor.impl;

import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.network.neighbor.NeighborMetrics;
import com.iota.iri.network.neighbor.NeighborState;
import com.iota.iri.network.pipeline.TransactionProcessingPipeline;
import com.iota.iri.network.protocol.*;
import com.iota.iri.network.protocol.message.MessageReader;
import com.iota.iri.network.protocol.message.MessageReaderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * {@link NeighborImpl} is an implementation of {@link Neighbor} using a {@link ByteChannel} as the source and
 * destination of data.
 * 
 * @param <T>
 */
public class NeighborImpl<T extends SelectableChannel & ByteChannel> implements Neighbor {

    private static final Logger log = LoggerFactory.getLogger(NeighborImpl.class);

    /**
     * The current state whether the neighbor is parsing a header or reading a message.
     */
    private enum ReadState {
        PARSE_HEADER, HANDLE_MESSAGE
    }

    // next stage in the processing of incoming data
    private TransactionProcessingPipeline txPipeline;

    // data to be written out to the neighbor
    private BlockingQueue<ByteBuffer> sendQueue = new ArrayBlockingQueue<>(100);
    private ByteBuffer currentToWrite;

    private NeighborState state = NeighborState.HANDSHAKING;
    private ReadState readState = ReadState.PARSE_HEADER;

    // ident
    private String domain;
    private String hostAddress;
    private int remoteServerSocketPort;
    private int protocolVersion;

    // we need the reference to the channel in order to register it for
    // write interests once messages to send are available.
    private T channel;
    private Selector selector;

    private NeighborMetrics metrics = new NeighborMetricsImpl();
    private MessageReader msgReader;
    private Handshake handshake = new Handshake();

    /**
     * Creates a new {@link NeighborImpl} using the given channel.
     * 
     * @param selector               the {@link Selector} which is associated with passed in channel
     * @param channel                the channel to use to read and write bytes from/to.
     * @param hostAddress            the host address (IP address) of the neighbor
     * @param remoteServerSocketPort the server socket port of the neighbor
     * @param txPipeline             the transaction processing pipeline to submit newly received transactions to
     */
    public NeighborImpl(Selector selector, T channel, String hostAddress, int remoteServerSocketPort,
            TransactionProcessingPipeline txPipeline) {
        this.hostAddress = hostAddress;
        this.remoteServerSocketPort = remoteServerSocketPort;
        this.selector = selector;
        this.channel = channel;
        this.txPipeline = txPipeline;
        this.msgReader = MessageReaderFactory.create(ProtocolMessage.HEADER, ProtocolMessage.HEADER.getMaxLength());
    }

    @Override
    public Handshake handshake() throws IOException {
        if (read() == -1) {
            handshake.setState(Handshake.State.FAILED);
        }
        return handshake;
    }

    @Override
    public int read() throws IOException {
        int bytesRead = msgReader.readMessage(channel);
        if (!msgReader.ready()) {
            return bytesRead;
        }
        ByteBuffer msg = msgReader.getMessage();
        msg.flip();
        switch (readState) {
            case PARSE_HEADER:
                if (!parseHeader(msg)) {
                    return -1;
                }
                // execute another read as we likely already have the message in the network buffer
                return read();

            case HANDLE_MESSAGE:
                handleMessage(msg);
                break;
            default:
                // do nothing
        }
        return bytesRead;
    }

    /**
     * Parses the header in the given {@link ByteBuffer} and sets up the message reader to read the bytes for a message
     * of the advertised type/size.
     * 
     * @param msg the {@link ByteBuffer} containing the header
     * @return whether the parsing was successful or not
     */
    private boolean parseHeader(ByteBuffer msg) {
        ProtocolHeader protocolHeader;
        try {
            protocolHeader = Protocol.parseHeader(msg);
        } catch (UnknownMessageTypeException e) {
            log.error("unknown message type received from {}, closing connection", getHostAddressAndPort());
            return false;
        } catch (InvalidProtocolMessageLengthException e) {
            log.error("{} is trying to send a message with an invalid length for the given message type, "
                    + "closing connection", getHostAddressAndPort());
            return false;
        }

        // if we are handshaking, then we must have a handshaking packet as the initial packet
        if (state == NeighborState.HANDSHAKING && protocolHeader.getMessageType() != ProtocolMessage.HANDSHAKE) {
            log.error("neighbor {}'s initial packet is not a handshaking packet, closing connection",
                    getHostAddressAndPort());
            return false;
        }

        // we got the header, now we want to read/handle the message
        readState = ReadState.HANDLE_MESSAGE;
        msgReader = MessageReaderFactory.create(protocolHeader.getMessageType(), protocolHeader.getMessageLength());
        return true;
    }

    /**
     * Relays the message to the component in charge of handling this message.
     * 
     * @param msg       the {@link ByteBuffer} containing the message (without header)
     */
    private void handleMessage(ByteBuffer msg) {
        switch (msgReader.getMessageType()) {
            case HANDSHAKE:
                handshake = Handshake.fromByteBuffer(msg);
                break;
            case TRANSACTION_GOSSIP:
                txPipeline.process(this, msg);
                break;
            default:
                // do nothing
        }
        // reset
        readState = ReadState.PARSE_HEADER;
        msgReader = MessageReaderFactory.create(ProtocolMessage.HEADER, ProtocolMessage.HEADER.getMaxLength());
    }

    @Override
    public int write() throws IOException {
        // previous message wasn't fully sent yet
        if (currentToWrite != null) {
            return writeMsg();
        }

        currentToWrite = sendQueue.poll();
        if (currentToWrite == null) {
            return 0;
        }
        return writeMsg();
    }

    private int writeMsg() throws IOException {
        int written = channel.write(currentToWrite);
        if (!currentToWrite.hasRemaining()) {
            currentToWrite = null;
        }
        return written;
    }

    @Override
    public void send(ByteBuffer buf) {
        // re-register write interest
        SelectionKey key = channel.keyFor(selector);
        if (key != null && key.isValid() && (key.interestOps() & SelectionKey.OP_WRITE) == 0) {
            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            selector.wakeup();
        }

        if (!sendQueue.offer(buf)) {
            metrics.incrDroppedSendPacketsCount();
        }
    }

    @Override
    public String getHostAddressAndPort() {
        if (remoteServerSocketPort == Neighbor.UNKNOWN_REMOTE_SERVER_SOCKET_PORT) {
            return hostAddress;
        }
        return String.format("%s:%d", hostAddress, remoteServerSocketPort);
    }

    @Override
    public String getHostAddress() {
        return hostAddress;
    }

    @Override
    public void setDomain(String domain) {
        this.domain = domain;
    }

    @Override
    public String getDomain() {
        return domain;
    }

    @Override
    public int getRemoteServerSocketPort() {
        return remoteServerSocketPort;
    }

    @Override
    public void setRemoteServerSocketPort(int port) {
        remoteServerSocketPort = port;
    }

    @Override
    public NeighborState getState() {
        return state;
    }

    @Override
    public void setState(NeighborState state) {
        if (this.state == NeighborState.MARKED_FOR_DISCONNECT) {
            return;
        }
        this.state = state;
    }

    @Override
    public NeighborMetrics getMetrics() {
        return metrics;
    }

    @Override
    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    @Override
    public int getProtocolVersion() {
        return protocolVersion;
    }

}
