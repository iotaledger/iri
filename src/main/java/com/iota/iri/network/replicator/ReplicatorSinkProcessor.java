package com.iota.iri.network.replicator;

import com.iota.iri.network.TCPNeighbor;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

class ReplicatorSinkProcessor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ReplicatorSinkProcessor.class);

    private final TCPNeighbor neighbor;

    public final static int CRC32_BYTES = 16;
    private final ReplicatorSinkPool replicatorSinkPool;
    private final int port;
    private int transactionPacketSize;

    public ReplicatorSinkProcessor(final TCPNeighbor neighbor,
                                   final ReplicatorSinkPool replicatorSinkPool,
                                   final int port, int transactionPacketSize) {
        this.neighbor = neighbor;
        this.replicatorSinkPool = replicatorSinkPool;
        this.port = port;
        this.transactionPacketSize = transactionPacketSize;
    }

    private boolean isMessageValid(byte[] message) {
        if (replicatorSinkPool.node.optimizeNetworkEnabled) {
            return (message.length == replicatorSinkPool.node.transactionSize || message.length == replicatorSinkPool.node.broadcastHashSize
                    || message.length == replicatorSinkPool.node.requestHashSize);
        } else {
            return (message.length == transactionPacketSize);
        }
    }

    @Override
    public void run() {
    	try {
    		Thread.sleep(1000);
    	}
    	catch (InterruptedException e) {
    		log.info("Interrupted");
    	}

        String remoteAddress = neighbor.getHostAddress();

        try {
            Socket socket;
            synchronized (neighbor) {
                Socket sink = neighbor.getSink();
                if ( sink == null ) {
                    log.info("Opening sink {}", remoteAddress);
                    socket = new Socket();
                    socket.setSoLinger(true, 0);
                    socket.setSoTimeout(30000);
                    neighbor.setSink(socket);
                }
                else {
                    // Sink already created
                    log.info("Sink {} already created", remoteAddress);
                    return;
                }
            }

            if (socket != null) {
                log.info("Connecting sink {}", remoteAddress);
                socket.connect(new InetSocketAddress(remoteAddress, neighbor.getPort()), 30000);
                if (!socket.isClosed() && socket.isConnected()) {
                    OutputStream out = socket.getOutputStream();
                    log.info("----- NETWORK INFO ----- Sink {} is connected", remoteAddress);

                    // Let neighbor know our tcp listener port
                    String fmt = "%0"+String.valueOf(ReplicatorSinkPool.PORT_BYTES)+"d";
                    byte [] portAsByteArray = new byte [10];
                    System.arraycopy(String.format(fmt, port).getBytes(), 0,
                            portAsByteArray, 0, ReplicatorSinkPool.PORT_BYTES);
                    out.write(portAsByteArray);

                    while (!replicatorSinkPool.shutdown && !neighbor.isStopped()) {
                        try {
                            ByteBuffer message = neighbor.getNextMessage();
                            if (neighbor.getSink() != null) {
                                if (neighbor.getSink().isClosed() || !neighbor.getSink().isConnected()) {
                                    log.info("----- NETWORK INFO ----- Sink {} got disconnected", remoteAddress);
                                    return;
                                } else {
                                    if ((message != null) && (neighbor.getSink() != null && neighbor.getSink().isConnected())
                                        && (neighbor.getSource() != null && neighbor.getSource().isConnected())) {

                                        byte[] bytes = message.array();

                                        if (isMessageValid(bytes)) {
                                            try {
                                                CRC32 crc32 = new CRC32();
                                                crc32.update(message.array());
                                                String crc32String = Long.toHexString(crc32.getValue());
                                                while (crc32String.length() < CRC32_BYTES) {
                                                    crc32String = "0"+crc32String;
                                                }

                                                if (replicatorSinkPool.node.optimizeNetworkEnabled) {
                                                    byte[] both = (byte[]) ArrayUtils.addAll(bytes, crc32String.getBytes());
                                                    out.write(both);
                                                } else {
                                                    out.write(message.array());
                                                    out.write(crc32String.getBytes());
                                                }

                                                out.flush();
                                                neighbor.incSentTransactions();
                                            } catch (IOException e2) {
                                                if (!neighbor.getSink().isClosed() && neighbor.getSink().isConnected()) {
                                                    out.close();
                                                    out = neighbor.getSink().getOutputStream();
                                                } else {
                                                    log.info("----- NETWORK INFO ----- Sink {} thread terminating",
                                                        remoteAddress);
                                                    return;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (InterruptedException e) {
                            log.error("Interrupted while waiting for send buffer");
                        }
                    }
                }
            }
        } catch (Exception e) {
            String reason = e.getMessage();
            if (reason == null || reason.equals("null")) {
                reason = "closed";
            }
            log.error("***** NETWORK ALERT ***** No sink to apiHost {}:{}, reason: {}", remoteAddress, neighbor.getPort(),
                    reason);
            synchronized (neighbor) {
                Socket sourceSocket = neighbor.getSource();
                if (sourceSocket != null && (sourceSocket.isClosed() || !sourceSocket.isConnected())) {
                    neighbor.setSource(null);
                }
                neighbor.setSink(null);
            }
        }

    }

}
