package com.iota.iri.service.storage;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Neighbor;
import com.iota.iri.service.Node;

public class ReplicatorSinkProcessor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ReplicatorSinkProcessor.class);

    private Neighbor neighbor;

    public ReplicatorSinkProcessor(Neighbor neighbor) {
        this.neighbor = neighbor;
    }

    @Override
    public void run() {

        String remoteAddress = neighbor.getAddress().getAddress().getHostAddress();
        try {
            if (neighbor.getSink() == null) {
                Socket socket = new Socket();
                socket.setSoTimeout(30000);
                socket.connect(new InetSocketAddress(remoteAddress, Replicator.REPLICATOR_PORT), 30000);
                OutputStream out = socket.getOutputStream();
                neighbor.setSink(socket);
                neighbor.setWaitingForSinkOpen(false);
                log.info("----- NETWORK INFO ----- Sink {} is open, configured = {}", remoteAddress, neighbor.isFlagged());
                while (!ReplicatorSinkPool.instance().shutdown) {
                    try {
                        ByteBuffer message = neighbor.getNextMessage();
                        if ((neighbor.getSink() != null && neighbor.getSink().isConnected())
                                && (neighbor.getSource() != null && neighbor.getSource().isConnected())) {
                            byte[] bytes = message.array();
                            if (bytes.length == Node.TRANSACTION_PACKET_SIZE && !socket.isClosed()) {
                                try {
                                    out.write(message.array());
                                } catch (IOException e2) {
                                    log.error("***** NETWORK ALERT ***** Error wrting to sink, closing connection");
                                    neighbor.setSink(null);
                                    neighbor.setSource(null);
                                    break;
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        log.error("Interrupted while waiting for send buffer");
                    }
                }
            }
        } catch (Exception e) {
            log.error("***** NETWORK ALERT ***** Could not create outbound connection to host {} port {}", remoteAddress, Replicator.REPLICATOR_PORT);
            neighbor.setSource(null);
            neighbor.setSink(null);            
            neighbor.setWaitingForSinkOpen(false);
            return;
        }

    }

}
