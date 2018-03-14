package com.iota.iri.network;

import com.iota.iri.network.exec.SendTPSLimiter;
import com.iota.iri.utils.Quiet;
import com.iota.iri.utils.Shutdown;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class TCPSink implements Closeable, Shutdown {

    private static final Logger log = LoggerFactory.getLogger(TCPSink.class);

    public static class Stats {
        private final long started = System.currentTimeMillis();

        private AtomicLong packetsQueued = new AtomicLong();
        private AtomicLong packetsSentFromQueue = new AtomicLong();

        private AtomicLong packetsSent = new AtomicLong();
        private AtomicLong packetBytesSent = new AtomicLong();

        private AtomicLong packetsRetried = new AtomicLong();

        private AtomicLong packetsDroppedBusy = new AtomicLong();
        private AtomicLong packetsDroppedTPSLimited = new AtomicLong();
        private AtomicLong permanentErrorsThrown = new AtomicLong();
        private AtomicLong packetsDroppedNoPermit = new AtomicLong();

        public long getPacketsQueued() {
            return packetsQueued.get();
        }

        public long getPacketsSentFromQueue() {
            return packetsSentFromQueue.get();
        }

        public long getPacketsSent() {
            return packetsSent.get();
        }

        public long getPacketsBytesSent() {
            return packetBytesSent.get();
        }

        public long getPacketsRetried() {
            return packetsRetried.get();
        }

        public long getPacketsDroppedBusy() {
            return packetsDroppedBusy.get();
        }

        public int getPacketsDroppedBusyPct() {
            long denominator = getPacketsDropped();
            if (denominator == 0) {
                return 0;
            }
            return (int) (getPacketsDroppedBusy() / denominator);
        }

        public long getPacketsDroppedTPSLimited() {
            return packetsDroppedTPSLimited.get();
        }


        public long getPermanentErrorsThrown() {
            return permanentErrorsThrown.get();
        }

        public long getPacketsDroppedNoPermit() {
            return packetsDroppedNoPermit.get();
        }

        public long getPacketsDropped() {
            return getPacketsDroppedBusy()
                    + getPacketsDroppedTPSLimited()
                    + getPermanentErrorsThrown()
                    + getPacketsDroppedNoPermit();
        }

        public Duration uptime() {
            return Duration.ofMillis(System.currentTimeMillis() - started);
        }

        // division by zero
        public int tps() {
            long uptimeSecs = uptime().getSeconds();
            if (uptimeSecs == 0) {
                return 0;
            }
            return (int) (getPacketsSent() / uptimeSecs);
        }
    }

    private final Object queueLock = new Object();
    private final int QUEUE_SIZE = 24;
    private final List<byte[]> queue = new ArrayList<>(QUEUE_SIZE);

    // how long we will wait in a single thread to establish a connection
    private final int so_timeout = (int) Node.SO_TIMEOUT.toMillis();

    // how many times we will retry a connection problem that is possibly recoverable
    private final int MAX_RETRIES = 4;

    // UP TO 5 THREADS CAN SIT WAITING WITH NO TIMOUT
    private final Semaphore timedAreaSemaphore = new Semaphore(5, true);
    private final int TIMED_AREA_REQUIRED_PERMITS = 1;

    // AFTER 60 millis, they will give up and queue the request if space available in the queue
    private final Semaphore workingAreaSemaphore = new Semaphore(1, true);
    private final int WORKING_AREA_WAIT_TIME = 60; // milliseconds

    // How many TPS max to allow in sending
    private final SendTPSLimiter transactionsPerSecondLimiter = new SendTPSLimiter(128);


    private final TCPNeighbor neighbor;
    private final Stats stats;

    private volatile boolean closed = false;

    private Socket socket;
    private OutputStream socketOutputStream;
    private int port;
    private boolean tcpPortBytesSent;
    private int offset;

    TCPSink(TCPNeighbor neighbor) {
        this.neighbor = neighbor;
        this.stats = new Stats();
    }


    private byte[] makePortBytes() {
        byte[] bytes = new byte[Node.PORT_BYTES];
        String fmt = "%0" + String.valueOf(Node.PORT_BYTES) + "d";
        fmt = String.format(fmt, port);
        System.arraycopy(fmt.getBytes(), 0, bytes, 0, Node.PORT_BYTES);
        return bytes;
    }


    private void closeConnections() {
        Quiet.close(socketOutputStream);
        Quiet.close(socket);
        socket = null;
        socketOutputStream = null;
    }

    public Stats getStats() {
        return stats;
    }

    @Override
    public void close() {
        closed = true;
        closeConnections();
        log.info("Closed Sink for {}, {}:{}", neighbor.hashCode(), neighbor.getHostAddress(), port);
    }

    @Override
    public boolean isShutdown() {
        return closed;
    }

    @Override
    public void shutdown() {
        close();
    }


    private boolean usable(Socket socket) {
        if (socket == null) {
            return false;
        } else if (socket.isClosed()) {
            return false;
        } else if (socket.isOutputShutdown() || socket.isInputShutdown()) {
            return false;
        }
        return socket.isConnected();
    }


    /**
     * @param errorControl if being checked after possibly recoverable error
     * @return false if closed, true if continue
     * @throws IOException if exception needs to be handled.
     */
    private boolean checkReady(boolean errorControl) throws IOException {
        if (socket == null || errorControl) {
            // CONTINUE
        } else if (usable(socket)) {
            return true;
        }

        if (socket != null) {
            closeConnections();
        }
        socket = new Socket();
        tcpPortBytesSent = false;
        try {
            socket.setSoLinger(true, 0);
            socket.setSoTimeout(so_timeout);

            InetSocketAddress insa = new InetSocketAddress(neighbor.getHostAddress(), port);
            // socket.isConnected() will be true after this
            // if connect fails then it WILL throw
            socket.connect(insa, so_timeout);
            socketOutputStream = socket.getOutputStream();
            return true;
        } catch (IOException ex) {
            if (closed) {
                return false;
            } else {
                log.info("----- NETWORK INFO ---- Sink error: {} ---- for {}, {}:{}", ex, neighbor.hashCode(), neighbor.getHostAddress(), port);
                throw ex;
            }
        }
    }

    private void socketSend(byte[] data) throws IOException {
        socketOutputStream.write(data);
        socketOutputStream.flush();
        transactionsPerSecondLimiter.addSent();
        stats.packetsSent.incrementAndGet();
        stats.packetBytesSent.addAndGet(data.length);
    }

    private void guardedSend(byte[] data) throws IOException {
        IOException exception;
        int retries = -1;
        while (true) {
            retries++;
            port = neighbor.getPort();
            boolean errorControl = retries != 0;

            if (!checkReady(errorControl)) {
                log.info("----- NETWORK INFO ----- Sink terminating ---- for {}, {}:{}", neighbor.hashCode(), neighbor.getHostAddress(), port);
                return;
            }
            try {
                byte[] portBytes = null;
                if (!tcpPortBytesSent) {
                    portBytes = makePortBytes();
                    socketOutputStream.write(portBytes);
                }
                // send packet
                socketSend(data);
                if (portBytes != null) {
                    tcpPortBytesSent = true;
                    stats.packetBytesSent.addAndGet(portBytes.length);
                }
                if (retries != 0) {
                    stats.packetsRetried.incrementAndGet();
                }

                synchronized (queueLock) {
                    // get the last ones first and send them
                    while (!queue.isEmpty()) {
                        if (transactionsPerSecondLimiter.isSend()) {
                            int last = queue.size() - 1;
                            byte[] packet = queue.get(last);
                            // send packet
                            socketSend(packet);
                            queue.remove(last);
                            stats.packetsSentFromQueue.incrementAndGet();
                        }
                    }
                }
                //
                //
                return;
                //
                //
            } catch (IOException io) {
                exception = io;
                if (closed) {
                    log.info("----- NETWORK INFO ----- terminating for {}, {}:{}", neighbor.hashCode(), neighbor.getHostAddress(), port);
                    return;
                }
                if (retries >= MAX_RETRIES) {
                    throw exception;
                }
                if (socket.isClosed()) {
                    log.info("----- NETWORK INFO ----- {} for {}, {}:{}", exception.toString(), neighbor.hashCode(), neighbor.getHostAddress(), port);
                    throw exception;
                }
                switch (exception.getMessage()) {
                    case "Broken pipe (Write failed)":
                        break;
                    case "Connection reset":
                        break;
                    default:
                        log.warn("----- NETWORK INFO ----- {} for {}, {}:{}", exception.toString(), neighbor.hashCode(), neighbor.getHostAddress(), port);
                        throw exception;
                }
            }
        }
    }

    private void enterWorkingArea(byte[] data) throws IOException {
        if (closed) {
            throw new IOException("sink already closed for " + neighbor.hashCode() + ", " + neighbor.getHostAddress() + ":" + port);
        }
        try {
            guardedSend(data);
        } catch (final Exception e) {
            closeConnections();
            stats.permanentErrorsThrown.incrementAndGet();
            throw e;
        } finally {
            if (closed) {
                closeConnections();
                synchronized (queueLock) {
                    queue.clear();
                }
            }
        }
    }

    /**
     * Internet latency of 20ms to 100ms
     * This ensures that if the wait is too long, the request is then queued for the thread
     * already inside to process or for the next thread to also process.
     */
    private void enterTimedWaiters(byte[] data) throws InterruptedException, IOException {
        if (workingAreaSemaphore.tryAcquire(WORKING_AREA_WAIT_TIME, TimeUnit.MILLISECONDS)) {
            try {
                enterWorkingArea(data);
            } finally {
                workingAreaSemaphore.release();
            }
        } else {
            synchronized (queueLock) {
                if (queue.size() < 10) {
                    queue.add(data);
                    stats.packetsQueued.incrementAndGet();
                } else {
                    stats.packetsDroppedBusy.incrementAndGet();
                }
            }
        }
    }

    /**
     * There are only 6 timedAreaSemaphore available.
     * If we can't get one, then there are already 6 callers
     * queued at the next queueLock.
     */
    private void enterRateLimited(byte[] data) throws InterruptedException, IOException {
        if (timedAreaSemaphore.tryAcquire(TIMED_AREA_REQUIRED_PERMITS)) {
            try {
                enterTimedWaiters(data);
            } finally {
                timedAreaSemaphore.release(1);
            }
        } else {
            stats.packetsDroppedNoPermit.incrementAndGet();
        }
    }

    void sendData(byte[] data) throws IOException, InterruptedException {
        if (transactionsPerSecondLimiter.isSend()) {
            enterRateLimited(data);
        } else {
            stats.packetsDroppedTPSLimited.incrementAndGet();
        }
    }
}