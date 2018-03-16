package com.iota.iri.network;

import com.iota.iri.network.exec.SendTPSLimiter;
import com.iota.iri.utils.CRC32ChecksumUtility;
import com.iota.iri.utils.Quiet;
import com.iota.iri.utils.Shutdown;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class TCPSink implements Closeable, Shutdown {

    private static final Logger log = LoggerFactory.getLogger(TCPSink.class);

    private static final int MAXIMUM_TRANSACTIONS_PER_SECOND_THROUGHPUT_PER_NEIGHBOR = 128;

    // THE QUEUE IS ALWAYS DRAINED BY THE CURRENT WORKER OR NEXT WORKER WAITING
    // IF NO WORKER IS WAITING AND THE QUEUE HAS JOBS, THEN THE CURRENT WORKER WILL
    // PICK THEM UP BEFORE EXITING
    private static final int QUEUE_SIZE = 24;

    private static final Duration CONNECTION_REFUSED_RETRY = Duration.ofSeconds(30);

    // how many times we will retry a connection problem that is possibly recoverable
    private static final int RECOVERABLE_SEND_PROBLEM_MAX_RETRIES = 4;

    // how long we will wait in a single thread to establish a connection
    private static final int SOCKET_TIMEOUT = (int) Node.SO_TIMEOUT.toMillis();

    // up to 5 threads can enter the timed waiting area
    private static final int TIMED_AREA_MAXIMUM_PERMITS = 5;
    private static final int TIMED_AREA_PERMITS_ISSUED_PER_THREAD = 1;

    // only one thread can be sending at any time
    private static final int WORKING_AREA_PERMITS = 1;
    private static final Duration WORKING_AREA_WAIT_TIME = Duration.ofMillis(60);


    private final Object queueLock = new Object();
    private final List<byte[]> queue = new ArrayList<>(QUEUE_SIZE);
    private AtomicLong connectionRefusedTimeMillis = new AtomicLong();
    private final Semaphore timedAreaSemaphore = new Semaphore(TIMED_AREA_MAXIMUM_PERMITS, true);
    private final Semaphore workingAreaSemaphore = new Semaphore(WORKING_AREA_PERMITS, true);
    private final SendTPSLimiter transactionsPerSecondLimiter = new SendTPSLimiter(MAXIMUM_TRANSACTIONS_PER_SECOND_THROUGHPUT_PER_NEIGHBOR);

    private final TCPNeighbor neighbor;
    private final Stats stats;

    private volatile boolean closed = false;
    private Socket socket;
    private OutputStream socketOutputStream;
    private int destinationPort;
    private boolean tcpPortBytesSent;


    public static class Stats {
        private final long started = System.currentTimeMillis();

        private AtomicLong packetsQueued = new AtomicLong();
        private AtomicLong packetsSentFromQueue = new AtomicLong();

        private AtomicLong packetsSent = new AtomicLong();
        private AtomicLong packetBytesSent = new AtomicLong();

        private AtomicLong packetsRetried = new AtomicLong();

        private AtomicLong packetsDroppedConnectionRefused = new AtomicLong();
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

        public long getPacketsDroppedConnectionRefused() {
            return packetsDroppedConnectionRefused.get();
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
            return getPacketsDroppedConnectionRefused()
                    + getPacketsDroppedBusy()
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

    TCPSink(TCPNeighbor neighbor) {
        this.neighbor = neighbor;
        this.stats = new Stats();
        this.destinationPort = neighbor.getPort();
    }


    private byte[] makeTCPRecieverPortBytes() {
        byte[] bytes = new byte[Node.PORT_BYTES];
        String fmt = "%0" + String.valueOf(Node.PORT_BYTES) + "d";
        fmt = String.format(fmt, neighbor.getTcpReceiverPort());
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
        log.debug("Closed Sink for {}, {}:{}", neighbor.hashCode(), neighbor.getHostAddress(), destinationPort);
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
        if (socket != null && !errorControl && usable(socket)) {
            return true;
        }
        if (socket != null) {
            closeConnections();
        }
        socket = new Socket();
        tcpPortBytesSent = false;

        try {
            socket.setSoLinger(true, 0);
            socket.setSoTimeout(SOCKET_TIMEOUT);

            this.destinationPort = neighbor.getPort();
            InetSocketAddress insa = new InetSocketAddress(neighbor.getHostAddress(), destinationPort);
            // socket.isConnected() will be true after this
            // if connect fails then it WILL throw
            socket.connect(insa, SOCKET_TIMEOUT);
            socketOutputStream = socket.getOutputStream();
            return true;
        } catch (IOException ex) {
            String reason = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            if (closed) {
                return false;
            } else {
                // some sort of connection refused problem
                connectionRefusedTimeMillis.set(System.currentTimeMillis());
                log.info("----- NETWORK INFO ---- {} ---- for {}, {}:{}", reason, neighbor.hashCode(), neighbor.getHostAddress(), destinationPort);
                throw ex;
            }
        }
    }

    private void socketSend(byte[] data) throws IOException {
        if (data.length != Node.TRANSACTION_PACKET_SIZE) {
            log.error("Wrong packet size: expected= {}, got= {}", Node.TRANSACTION_PACKET_SIZE, data.length);
            throw new RuntimeException("WRONG TCP PACKET SIZE: " + data.length);
        }
        byte[] all = ArrayUtils.addAll(data, CRC32ChecksumUtility.makeCheckumBytes(data));
        socketOutputStream.write(all);
        socketOutputStream.flush();
        transactionsPerSecondLimiter.addSent();
        stats.packetsSent.incrementAndGet();
        stats.packetBytesSent.addAndGet(data.length);
    }

    private void guardedSend(final byte[] data) throws IOException {
        IOException exception;
        int retries = -1;

        while (true) {
            retries++;
            boolean errorControl = retries != 0;
            if (!checkReady(errorControl)) {
                log.info("----- NETWORK INFO ----- Sink terminating ---- for {}, {}:{}", neighbor.hashCode(), neighbor.getHostAddress(), neighbor.getPort());
                return;
            }

            try {
                byte[] portBytes = null;
                if (!tcpPortBytesSent) {
                    portBytes = makeTCPRecieverPortBytes();
                    socketOutputStream.write(portBytes);
                }
                // send packet
                socketSend(data);
                if (portBytes != null) {
                    tcpPortBytesSent = true;
                    stats.packetBytesSent.addAndGet(portBytes.length);
                    if (log.isDebugEnabled()) log.debug("TCP PORT BYTES SENT: {}", neighbor.getTcpReceiverPort());
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
                    log.info("----- NETWORK INFO ----- terminating for {}, {}:{}", neighbor.hashCode(), neighbor.getHostAddress(), destinationPort);
                    return;
                }
                if (retries >= RECOVERABLE_SEND_PROBLEM_MAX_RETRIES) {
                    throw exception;
                }
                if (socket.isClosed()) {
                    log.info("----- NETWORK INFO ----- {} for {}, {}:{}", exception.toString(), neighbor.hashCode(), neighbor.getHostAddress(), destinationPort);
                    throw exception;
                }

                String reason = exception.getMessage();
                if (reason == null) {
                    reason = exception.getClass().getSimpleName();
                }
                switch (reason) {
                    case "Broken pipe (Write failed)":
                        break;
                    case "Connection reset":
                        break;
                    case "Connection refused":
                        connectionRefusedTimeMillis.set(System.currentTimeMillis());
                    default:
                        log.warn("----- NETWORK INFO ----- {} for {}, {}:{}", exception.toString(), neighbor.hashCode(), neighbor.getHostAddress(), destinationPort);
                        throw exception;
                }
            }
        }
    }

    private boolean isConnectionWait() {
        long timeSince = System.currentTimeMillis() - connectionRefusedTimeMillis.get();
        long connectionRetryTimeOutstanding = CONNECTION_REFUSED_RETRY.toMillis() - timeSince;
        if (connectionRetryTimeOutstanding < 0) {
            return false;
        }
        synchronized (queueLock) {
            stats.packetsDroppedConnectionRefused.addAndGet(queue.size() + 1);
            queue.clear();
            return true;
        }
    }

    private void enterWorkingArea(final byte[] data) throws IOException {
        if (closed) {
            throw new IOException("sink already closed for " + neighbor.hashCode() + ", " + neighbor.getHostAddress() + ":" + destinationPort);
        }
        if (isConnectionWait()) {
            return;
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
        if (workingAreaSemaphore.tryAcquire(WORKING_AREA_WAIT_TIME.toMillis(), TimeUnit.MILLISECONDS)) {
            try {
                enterWorkingArea(data);
            } finally {
                workingAreaSemaphore.release();
            }
        } else {
            synchronized (queueLock) {
                while (queue.size() >= QUEUE_SIZE) {
                    queue.remove(queue.size() - 1);
                    stats.packetsDroppedBusy.incrementAndGet();
                }
                queue.add(data);
                stats.packetsQueued.incrementAndGet();
            }
        }
    }

    /**
     * There are only 6 timedAreaSemaphore available.
     * If we can't get one, then there are already 6 callers
     * queued at the next queueLock.
     */
    private void enterRateLimited(byte[] data) throws InterruptedException, IOException {
        if (timedAreaSemaphore.tryAcquire(TIMED_AREA_PERMITS_ISSUED_PER_THREAD)) {
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
        if (!isConnectionWait()) {
            if (transactionsPerSecondLimiter.isSend()) {
                enterRateLimited(data);
            } else {
                stats.packetsDroppedTPSLimited.incrementAndGet();
            }
        }
    }
}