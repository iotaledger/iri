package com.iota.iri.network;

import com.iota.iri.network.exec.StripedExecutor;
import com.iota.iri.utils.CRC32ChecksumUtility;
import com.iota.iri.utils.Quiet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


/**
 * The starting static method call is at the bottom of the class.
 */
public final class TCPSource implements CompletionHandler<Long, String>, Closeable {

    private static final Logger log = LoggerFactory.getLogger(TCPSource.class);

    private static final String PORT_TASK = "PORT";
    private static final String TRANSACTION_TASK = "TRANSACTION";
    private static final double CHECKSUM_MAX_ERRORS_RATIO = 0.02;

    public static class Stats {
        private final long started = System.currentTimeMillis();

        private AtomicLong packetsRecieved = new AtomicLong();
        private AtomicLong packetsProcessed = new AtomicLong();
        private AtomicLong packetsBytesProcessed = new AtomicLong();
        private AtomicLong packetsCRCBAD = new AtomicLong();

        public Duration uptime() {
            return Duration.ofMillis(System.currentTimeMillis() - started);
        }


        // division by zero
        public int tps() {
            long uptimeSecs = uptime().getSeconds();
            if (uptimeSecs == 0) {
                return 0;
            }
            return (int) (getPacketsRecieved() / uptimeSecs);
        }

        public long getPacketsRecieved() {
            return packetsRecieved.get();
        }

        public long getPacketsProcessed() {
            return packetsProcessed.get();
        }

        public long getPacketsBytesProcessed() {
            return packetsBytesProcessed.get();
        }

        public long getPacketsCRCBAD() {
            return packetsCRCBAD.get();
        }
    }


    private final Object lock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Stats stats = new Stats();

    private final AsynchronousSocketChannel channel;
    private final TCPNeighbor neighbor;
    private final StripedExecutor<Neighbor, byte[]> stripedExecutor;
    private final StripedExecutor.StripeManager stripeManager;


    private final ByteBuffer portBuffer;
    private final ByteBuffer[] portBuffers;
    private final ByteBuffer[] transactionBuffers;

    private TCPSource(AsynchronousSocketChannel channel, TCPNeighbor neighbor, StripedExecutor<Neighbor, byte[]> stripedExecutor) {
        this.channel = channel;
        this.neighbor = neighbor;
        this.stripedExecutor = stripedExecutor;
        this.stripeManager = new StripedExecutor.StripeManager(4, "TCPNeighbor-IN");


        // Enough to buffer 8 transactions
        // ~16,670 bytes (16K)
        this.portBuffer = ByteBuffer.allocate(Node.PORT_BYTES);
        this.portBuffers = new ByteBuffer[]{

                portBuffer,

                ByteBuffer.wrap(new byte[Node.TRANSACTION_PACKET_SIZE]),
                ByteBuffer.wrap(new byte[Node.CRC32_BYTES]),

                ByteBuffer.wrap(new byte[Node.TRANSACTION_PACKET_SIZE]),
                ByteBuffer.wrap(new byte[Node.CRC32_BYTES]),

                ByteBuffer.wrap(new byte[Node.TRANSACTION_PACKET_SIZE]),
                ByteBuffer.wrap(new byte[Node.CRC32_BYTES]),

                ByteBuffer.wrap(new byte[Node.TRANSACTION_PACKET_SIZE]),
                ByteBuffer.wrap(new byte[Node.CRC32_BYTES]),

                ByteBuffer.wrap(new byte[Node.TRANSACTION_PACKET_SIZE]),
                ByteBuffer.wrap(new byte[Node.CRC32_BYTES]),

                ByteBuffer.wrap(new byte[Node.TRANSACTION_PACKET_SIZE]),
                ByteBuffer.wrap(new byte[Node.CRC32_BYTES]),

                ByteBuffer.wrap(new byte[Node.TRANSACTION_PACKET_SIZE]),
                ByteBuffer.wrap(new byte[Node.CRC32_BYTES]),

                ByteBuffer.wrap(new byte[Node.TRANSACTION_PACKET_SIZE]),
                ByteBuffer.wrap(new byte[Node.CRC32_BYTES]),
        };

        this.transactionBuffers = new ByteBuffer[portBuffers.length - 1];
        System.arraycopy(portBuffers, 1, this.transactionBuffers, 0, portBuffers.length - 1);
    }

    public Stats getStats() {
        return stats;
    }

    private String identifyNeighbor() {
        return neighbor.hashCode() + ", " + neighbor.getHostAddress() + ":"
                + neighbor.getPort();
    }

    private ByteBuffer getDataPart() {
        return transactionBuffers[0];
    }

    private ByteBuffer getCRC32Part() {
        return transactionBuffers[1];
    }

    private void showBuffers(String task, long remaining, ByteBuffer[] buffers) {
        // USED FOR DEBUGGING
        synchronized (lock) {
            log.info("{} remaining= {} SHOWING BUFFERS", task, remaining);
            for (int n = 0; n < buffers.length; n++) {
                ByteBuffer bb = buffers[n];
                log.info("{} \t#{} {} \t\tcap= {} \tlim= {} \tpos= {} \trem= {}", task, n, bb.hashCode(),
                        bb.capacity(), bb.limit(), bb.position(), bb.remaining());
            }
            log.info("================================");
        }
    }

    private void shift() {
        transactionBuffers[1].clear();
        transactionBuffers[2].clear();
        Collections.rotate(Arrays.asList(transactionBuffers), -2);
    }

    private void handlePortData() {
        byte[] bytes = portBuffer.array();
        String str = new String(bytes);
        int destPort = (int) Long.parseLong(str);
        neighbor.setPort(destPort);
        log.debug("neighbor port set to {}", destPort);
        try {
            neighbor.setSource(this);
        } catch (Exception e) {
            log.warn("TCPSource cannot be set: {} for {}", Objects.toString(e.getMessage(), e.toString()), identifyNeighbor());
            close(channel);
        }
    }


    private void processTransaction() {
        byte[] data = getDataPart().array();
        byte[] checksumData = getCRC32Part().array();
        long processed = stats.packetsProcessed.incrementAndGet();

        if (log.isTraceEnabled()) {
            log.trace("Data: '{}'", new String(data));
            log.trace("Checksum: '{}'", new String(checksumData));
        }
        byte[] checksumExpected = CRC32ChecksumUtility.makeCheckumBytes(data);

        if (Arrays.equals(checksumData, checksumExpected)) {
            stats.packetsBytesProcessed.addAndGet(data.length + checksumData.length);
            stripedExecutor.submitStripe(stripeManager.stripe(), () -> stripedExecutor.process(neighbor, data));

        } else {
            long bad = stats.packetsCRCBAD.incrementAndGet();
            double ratio = bad / processed;
            String msg = String.format("for %s:\n     total= %d,      bad= %d,      ratio= %s", identifyNeighbor(), processed, bad, ratio);
            if (processed > 50 && ratio > CHECKSUM_MAX_ERRORS_RATIO) {
                throw new RuntimeException("TOO MANY CRC32 CHECKSUM ERRORS " + msg);
            } else {
                log.debug("CRC32 CHECKSUM ERROR {}", msg);
            }
        }
    }


    @Override
    public void completed(final Long result, final String task) {
        final ByteBuffer[] buffers;
        final long remaining;

        try {
            switch (task) {
                case PORT_TASK:
                    buffers = portBuffers;
                    remaining = portBuffer.remaining();
                    if (remaining == 0) {
                        stats.packetsRecieved.incrementAndGet();
                        handlePortData();
                        completed(null, TRANSACTION_TASK);
                    }
                    break;

                case TRANSACTION_TASK:
                    buffers = transactionBuffers;
                    remaining = getDataPart().remaining() + getCRC32Part().remaining();
                    if (remaining == 0) {
                        stats.packetsRecieved.incrementAndGet();
                        processTransaction();
                        shift(); // if the data is already cached, on the next round it will show as all received.
                        completed(null, TRANSACTION_TASK);
                    }
                    break;

                default:
                    throw new RuntimeException("UNREACHABLE: " + task);
            }
            if (remaining > 0) {
                channel.read(buffers, 0, buffers.length, Long.MAX_VALUE, TimeUnit.SECONDS, task, this);
            }
            if (remaining < 0) {
                throw new RuntimeException(task + " - REMAINING IS NEGATIVE (" + remaining + ") for " + identifyNeighbor());
            }
        } catch (Throwable throwable) {
            if (!closed.get()) {
                log.warn(throwable.getMessage() + " - while handling " + task + " for " + identifyNeighbor(), throwable);
                close();
                if (throwable instanceof RuntimeException) {
                    throw (RuntimeException) throwable;
                } else {
                    throw new RuntimeException(throwable);
                }
            }
        }
    }

    @Override
    public void failed(Throwable exc, String task) {
        String reason = exc == null ? "UNKNOWN" : exc.getMessage() == null ? exc.getClass().getSimpleName() : exc.getMessage();
        log.error("***** NETWORK ALERT ***** task= '{}', neighbor= '{}', message= : '{}'", task, identifyNeighbor(), reason);
        close();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (channel != null) {
                if (channel.isOpen()) {
                    close(channel);
                }
                Quiet.run(() -> {
                    for (ByteBuffer bb : portBuffers) bb.clear();
                });
                Arrays.fill(portBuffers, null);
                Arrays.fill(transactionBuffers, null);
                log.info("Channel {} closed: {}", identifyNeighbor(), channel);
            }
        }
    }


    /************************************
     *     STATIC METHOD
     *     STARTS HERE
     ***********************************/
    public static CompletionHandler<AsynchronousSocketChannel, Void> newConnectionHandler(
            AsynchronousServerSocketChannel listener,
            NeighborManager neighborManager,
            StripedExecutor<Neighbor, byte[]> stripedExecutor,
            int maxPeers,
            boolean testnet) {

        return new CompletionHandler<AsynchronousSocketChannel, Void>() {

            private void rejectConnection(AsynchronousSocketChannel ch, InetSocketAddress address) {
                try {
                    String connType = neighborManager.addRejectedAddress(address.getHostName())
                            ? "connected" : "Reconnected";

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

                // Trigger to Accept the next connection!
                listener.accept(null, this);

                final InetSocketAddress inetSocketAddress;
                try {
                    inetSocketAddress = (InetSocketAddress) channel.getRemoteAddress();
                    log.debug("----- NETWORK INFO ----- TCP channel initializing for {}", inetSocketAddress);

                    final String hostAddress = inetSocketAddress.getAddress().getHostAddress();
                    final String hostString = inetSocketAddress.getHostString();

                    // Match IP first and HOSTNAME second
                    TCPNeighbor neighbor = neighborManager.findFirst(TCPNeighbor.class,
                            n -> hostAddress.equals(n.getAddress().getAddress().getHostAddress())
                                    || hostString.equals(n.getAddress().getHostString()));

                    if (neighbor == null) {
                        if (testnet && neighborManager.count(TCPNeighbor.class::isInstance) < maxPeers) {
                            neighbor = neighborManager.newTCPNeighbor(inetSocketAddress, false);
                            neighborManager.add(neighbor);
                        } else {
                            rejectConnection(channel, inetSocketAddress);
                            return;
                        }
                    }

                    if (!neighbor.isStopped()) {
                        TCPSource handler = new TCPSource(channel, neighbor, stripedExecutor);
                        log.info("----- NETWORK INFO ----- TCP channel established with {}", inetSocketAddress);
                        handler.completed(null, PORT_TASK);

                    } else {
                        log.warn("Cannot create TCP channel for Neighbor {} that is already stopped: {}",
                                neighbor.hashCode(), inetSocketAddress);
                        close(channel);
                    }
                } catch (Exception e) {
                    log.error("Can't complete: " + channel, e);
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
                log.debug("failed on start", e);
            }
        };
    }

    private static void close(AsynchronousSocketChannel channel) {
        Quiet.run(channel::shutdownInput);
        Quiet.run(channel::shutdownOutput);
        Quiet.close(channel);
    }
}
