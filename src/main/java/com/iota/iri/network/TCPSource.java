package com.iota.iri.network;

import com.iota.iri.network.exec.StripedExecutor;
import com.iota.iri.utils.CRCCheckumUtility;
import com.iota.iri.utils.Quiet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * The starting static method call is at the bottom of the class.
 */
public class TCPSource implements CompletionHandler<Long, String>, Closeable {

    private static final Logger log = LoggerFactory.getLogger(TCPSource.class);

    private static final String PORT_TASK = "port";
    private static final String TRANSACTION_TASK = "transaction";
    private static final double CHECKSUM_MAX_ERRORS_RATIO = 0.02;

    private final AtomicBoolean closed = new AtomicBoolean();

    private final AsynchronousSocketChannel channel;
    private final TCPNeighbor neighbor;
    private final StripedExecutor<Neighbor, byte[]> stripedExecutor;

    private final AtomicInteger good = new AtomicInteger(0);
    private final AtomicInteger bad = new AtomicInteger(0);


    private final ByteBuffer portBuffer;
    private final ByteBuffer[] buffers;

    private TCPSource(AsynchronousSocketChannel channel, TCPNeighbor neighbor, StripedExecutor<Neighbor, byte[]> stripedExecutor) {
        this.channel = channel;
        this.neighbor = neighbor;
        this.stripedExecutor = stripedExecutor;


        // Enough to buffer 10 transactions
        // ~16,670 bytes (16K)
        this.portBuffer = ByteBuffer.allocate(Node.PORT_BYTES);
        this.buffers = new ByteBuffer[]{

                portBuffer,

                ByteBuffer.allocate(Node.TRANSACTION_PACKET_SIZE),
                ByteBuffer.allocate(Node.CRC32_BYTES),

                ByteBuffer.allocate(Node.TRANSACTION_PACKET_SIZE),
                ByteBuffer.allocate(Node.CRC32_BYTES),

                ByteBuffer.allocate(Node.TRANSACTION_PACKET_SIZE),
                ByteBuffer.allocate(Node.CRC32_BYTES),

                ByteBuffer.allocate(Node.TRANSACTION_PACKET_SIZE),
                ByteBuffer.allocate(Node.CRC32_BYTES),

                ByteBuffer.allocate(Node.TRANSACTION_PACKET_SIZE),
                ByteBuffer.allocate(Node.CRC32_BYTES),

                ByteBuffer.allocate(Node.TRANSACTION_PACKET_SIZE),
                ByteBuffer.allocate(Node.CRC32_BYTES),

                ByteBuffer.allocate(Node.TRANSACTION_PACKET_SIZE),
                ByteBuffer.allocate(Node.CRC32_BYTES),

                ByteBuffer.allocate(Node.TRANSACTION_PACKET_SIZE),
                ByteBuffer.allocate(Node.CRC32_BYTES),

                ByteBuffer.allocate(Node.TRANSACTION_PACKET_SIZE),
                ByteBuffer.allocate(Node.CRC32_BYTES),

                ByteBuffer.allocate(Node.TRANSACTION_PACKET_SIZE),
                ByteBuffer.allocate(Node.CRC32_BYTES)};
    }

    private String identifyNeighbor() {
        return neighbor.hashCode() + ", " + neighbor.getHostAddress() + ":"
                + neighbor.getPort();
    }

    private ByteBuffer getDataPart() {
        return buffers[1];
    }

    private ByteBuffer getCRC32Part() {
        return buffers[2];
    }

    private int getRemainingForTransaction() {
        return getDataPart().remaining() + getCRC32Part().remaining();
    }

    private void shift() {
        buffers[1].clear();
        buffers[2].clear();
        List<ByteBuffer> sublist = Arrays.asList(buffers).subList(1, buffers.length);
        Collections.rotate(sublist, -2);
    }

    private void handlePortData() {
        if (portBuffer.hasRemaining()) {
            throw new RuntimeException(
                    "Port buffer should not have any remaining: " + portBuffer.remaining()
                            + " for" + identifyNeighbor());
        }
        byte[] bytes = portBuffer.array();
        String str = new String(bytes);
        int destPort = (int) Long.parseLong(str);
        neighbor.setPort(destPort);
        try {
            neighbor.setSource(this);
        } catch (Exception e) {
            log.warn("TCPSource cannot be set: " + Objects.toString(e.getMessage(), "UNKNOWN")
                    + "for " + identifyNeighbor());
            close(channel);
            return;
        }
    }


    private void processTransaction() {
        byte[] clonedData = getDataPart().array().clone();
        byte[] crc32Data = getCRC32Part().array();
        if (CRCCheckumUtility.isOK(clonedData, crc32Data)) {
            log.info("CRC32 CHECKSUM OK");
            good.incrementAndGet();
            stripedExecutor.submitStripe("TCPNeighbor-IN", () -> stripedExecutor.process(neighbor, clonedData));
        } else {
            int goodCnt = good.get();
            int badCnt = bad.incrementAndGet();
            // 2/100 == 0.02
            double ratio = badCnt / goodCnt;

            String msg = "for " + identifyNeighbor() + ":\n"
                    + "     good= " + goodCnt + ", "
                    + "     bad= " + badCnt + ", "
                    + "     ratio= " + ratio;

            if (ratio > CHECKSUM_MAX_ERRORS_RATIO) {
                throw new RuntimeException("TOO MANY CRC32 CHECKSUM ERRORS " + msg);
            } else {
                log.warn("CRC32 CHECKSUM ERROR " + msg);
            }
        }
    }

    @Override
    public void completed(Long result, String task) {
        final long remaining;
        final ByteBuffer[] cache;
        try {
            switch (task) {
                case PORT_TASK:
                    remaining = portBuffer.remaining();
                    if (remaining == 0) {
                        handlePortData();
                        // no need to shift after port
                        // and we can even null the element now
                        buffers[0] = null;
                        completed(null, TRANSACTION_TASK);
                    }
                    break;

                case TRANSACTION_TASK:
                    remaining = getRemainingForTransaction();
                    if (remaining == 0) {
                        processTransaction();
                        shift();
                        completed(null, TRANSACTION_TASK);
                    }
                    break;
                default:
                    throw new RuntimeException("UNREACHABLE: " + task);
            }

            if (remaining > 0) {
                log.info("Missing {} bytes for '{}' - going back for {} more", task, identifyNeighbor(), remaining);
                channel.read(buffers, 0, buffers.length, Long.MAX_VALUE, TimeUnit.SECONDS, task, this);
            } else {
                throw new RuntimeException("REMAINING CAN NOT BE NEGATIVE: " + remaining + " for " + identifyNeighbor());
            }
        } catch (Exception e) {
            log.error("Can't complete channel for : " + identifyNeighbor(), e);
            close();
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void failed(Throwable exc, String task) {
        log.error("***** NETWORK ALERT ***** task= '{}', neighbor= '{}', message= : '{}'", task, identifyNeighbor(), exc.getMessage());
        close();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (channel != null) {
                if (channel.isOpen()) {
                    close(channel);
                }
                for (int n = 0; n < buffers.length; n++) {
                    buffers[n].clear();
                    buffers[n] = null;
                }
                closed.set(true);
                log.info("Channel {} closed: {}", identifyNeighbor(), channel);
            }
        }
    }


    /************************************
     *
     *
     *
     *     STATIC METHOD
     *     STARTS HERE
     *
     *
     ***********************************/
    public static CompletionHandler<AsynchronousSocketChannel, Void> newConnectionHandler(
            AsynchronousServerSocketChannel listener,
            NeighborManager neighborManager,
            StripedExecutor stripedExecutor,
            int maxPeers, boolean testnet) {

        return new CompletionHandler<AsynchronousSocketChannel, Void>() {

            private void rejectConnection(AsynchronousSocketChannel ch, InetSocketAddress address) {
                try {
                    String connType = neighborManager.addRejectedAddress(address.getHostName())
                            ? "connected" : "REconnected";

                    String extra = "";
                    if (testnet && neighborManager.count(TCPNeighbor.class::isInstance) >= maxPeers) {
                        extra = " (max-peers allowed is " + maxPeers + ")";
                    }
                    log.info("***** NETWORK ALERT ***** Refused {} from STRANGE tcp://{}{}",
                            connType, address, extra);

                } finally {
                    close(ch);
                }
            }

            @Override
            public void completed(AsynchronousSocketChannel channel, Void attachment) {
                // MUST DO - Trigger to Accept the next connection!
                listener.accept(null, this);

                final InetSocketAddress inetSocketAddress;
                try {
                    inetSocketAddress = (InetSocketAddress) channel.getRemoteAddress();

                    log.info("----- NETWORK INFO ----- TCP NIO channel initializing for {}", inetSocketAddress);
                    TCPNeighbor neighbor = neighborManager.findFirst(TCPNeighbor.class,
                            n -> n.getAddress().equals(inetSocketAddress));

                    if (neighbor == null) {
                        if (testnet && neighborManager.count(TCPNeighbor.class::isInstance) < maxPeers) {
                            neighbor = neighborManager.newTCPNeighbor(inetSocketAddress, false);
                            neighborManager.add(neighbor);
                        } else {
                            rejectConnection(channel, inetSocketAddress);
                            return;
                        }
                    }

                    // check if we are stopped
                    if (neighbor.isStopped()) {
                        log.warn("Cannot make new TCP NIO channel with Neighbor {} that is already stopped: {}",
                                neighbor.hashCode(), inetSocketAddress);
                        close(channel);
                        return;
                    }

                    TCPSource handler = new TCPSource(channel, neighbor, stripedExecutor);
                    handler.completed(null, PORT_TASK);
                } catch (Exception e) {
                    log.error("Can't complete channel for: " + channel, e);
                    close(channel);
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    } else {
                        throw new RuntimeException(e);
                    }
                }
            }


            @Override
            public void failed(Throwable e, Void attachment) {
                log.warn("AsynchronousSocketChannel failed on start", e);
            }
        };
    }

    private static void close(AsynchronousSocketChannel channel) {
        Quiet.run(channel::shutdownInput);
        Quiet.run(channel::shutdownOutput);
        Quiet.close(channel);
    }
}
