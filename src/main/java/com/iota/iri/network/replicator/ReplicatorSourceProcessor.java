package com.iota.iri.network.replicator;

import com.iota.iri.network.Neighbor;
import com.iota.iri.network.Node;
import com.iota.iri.network.TCPNeighbor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Objects;
import java.util.zip.CRC32;

class ReplicatorSourceProcessor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ReplicatorSourceProcessor.class);

    private final static int TRANSACTION_PACKET_SIZE = Node.TRANSACTION_PACKET_SIZE;

    private final Socket connection;
    private final Node node;
    private final int maxPeers;
    private final boolean testnet;
    private final ReplicatorSinkPool replicatorSinkPool;

    ReplicatorSourceProcessor(final ReplicatorSinkPool replicatorSinkPool,
                              final Socket connection,
                              final Node node,
                              final int maxPeers,
                              final boolean testnet) {
        this.connection = connection;
        this.node = node;
        this.maxPeers = maxPeers;
        this.testnet = testnet;
        this.replicatorSinkPool = replicatorSinkPool;
    }

    @Override
    public void run() {

        final InetSocketAddress inetSocketAddress = (InetSocketAddress) connection.getRemoteSocketAddress();
        final String hostAddress = inetSocketAddress.getAddress().getHostAddress();

        boolean belongsToAnotherSourceProcessor = false;
        TCPNeighbor neighbor = null;

        try {
            for (Neighbor n : node.getNeighbors()) {
                if (n instanceof TCPNeighbor) {
                    TCPNeighbor tcpNeighbor = (TCPNeighbor) n;
                    if (tcpNeighbor.getHostAddress().equals(hostAddress)) {
                        neighbor = tcpNeighbor;
                        break; // we found it!
                    }
                }
            }

            if (neighbor == null) {
                if (testnet && Neighbor.getNumPeers() < maxPeers) {
                    neighbor = new TCPNeighbor(inetSocketAddress, false);
                    node.getNeighbors().add(neighbor);
                    Neighbor.incNumPeers();
                } else {
                    String hostName = inetSocketAddress.getHostName();
                    if (Node.rejectedAddresses.add(hostName)) {
                        String more = (testnet && Neighbor.getNumPeers() >= maxPeers) ? " (max-peers allowed is " + maxPeers + ")" : "";
                        log.info("***** NETWORK ALERT ***** Got connected from unknown neighbor tcp://{} ({}) - closing connection{}",
                            hostName + ":" + inetSocketAddress.getPort(), hostAddress, more);
                    }
                    return;
                }
            }

            if (neighbor.getSource() != null) {
                log.info("Source {} already connected", hostAddress);
                belongsToAnotherSourceProcessor = true;
                return;
            }
            neighbor.setSource(connection);


            try (InputStream inputStream = new BufferedInputStream(connection.getInputStream())) {

                {   // STAGE 1 - Read neighbors tcp listener port number and set it for the Sink
                    byte[] buffer = new byte[ReplicatorSinkPool.PORT_BYTES];
                    int len = fillBuffer(inputStream, buffer, buffer.length);
                    if (len == -1 || connection.isClosed()) {
                        log.error("Did not receive neighbors listener port: {}", len == -1 ? "EOF" : "connection closed");
                        return;
                    }

                    neighbor.setTcpPort((int) Long.parseLong(new String(buffer)));

                    if (neighbor.getSink() == null) {
                        log.info("Creating sink for {}", neighbor.getHostAddress());
                        replicatorSinkPool.createSink(neighbor);
                    }
                    if (connection.isConnected()) {
                        log.info("----- NETWORK INFO ----- Source {} is connected", hostAddress);
                    }
                }

                {   // STAGE 2 - ONGOING - keep reading transaction packets
                    connection.setSoTimeout((int) Duration.ofHours(1).toMillis());  // blocking but not forever!

                    while (!neighbor.isStopped()) {
                        byte[] buffer = new byte[TRANSACTION_PACKET_SIZE + ReplicatorSinkProcessor.CRC32_BYTES];
                        if (fillBuffer(inputStream, buffer, buffer.length) == -1 || connection.isClosed()) {
                            return;
                        }
                        try {
                            if (validChecksum(buffer)) {
                                node.preProcessReceivedData(buffer, inetSocketAddress, "tcp");
                            }
                        } catch (Exception e) {
                            log.error("Unexpected error processing data: {}", Objects.toString(e.getMessage(), e.getClass().getName()), e);
                            neighbor.incInvalidTransactions();
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("***** NETWORK ALERT ***** TCP IO: {}, neighbor= {}",
                Objects.toString(e.getMessage(), e.getClass().getName()), hostAddress);
        } finally {
            try {
                // Closes IO - IN and OUT and closes trhe socket too.
                connection.close();
            } catch (IOException ignore) {
            }
            if (neighbor != null && !belongsToAnotherSourceProcessor) {
                replicatorSinkPool.shutdownSink(neighbor);
                neighbor.setSource(null);
                neighbor.setSink(null);
            }
        }
    }

    /**
     * Verifies that the checksum matches a calculated checksum against the same data.
     *
     * @param buffer a byte[] containing first the transaction packet and then the checksum packet.
     * @return true if the checksum calculated matches the checksum provided.
     */
    private static boolean validChecksum(byte[] buffer) {
        CRC32 crc32 = new CRC32();
        crc32.update(buffer, 0, TRANSACTION_PACKET_SIZE);
        String crc32_string = StringUtils.leftPad(Long.toHexString(crc32.getValue()), ReplicatorSinkProcessor.CRC32_BYTES, "0");
        byte[] crc32_bytes = crc32_string.getBytes();
        for (int i = 0; i < ReplicatorSinkProcessor.CRC32_BYTES; i++) {
            if (crc32_bytes[i] != buffer[TRANSACTION_PACKET_SIZE + i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param is     The InputStream to read (preferably buffered).
     * @param buffer the byte[] to fill with read data.
     * @param max    The maximum number of bytes to read.
     * @return -1 for EOF or the number of bytes read.
     * @throws IOException If an IO exception occurs.
     */
    private static int fillBuffer(InputStream is, byte[] buffer, int max) throws IOException {
        int offset = 0;
        int len;
        while ((len = is.read(buffer, offset, max - offset)) != -1) {
            offset += len;
            if (offset >= buffer.length) {
                return offset;
            }
        }
        return len;
    }
}