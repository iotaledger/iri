package com.iota.iri.service.storage;

import java.io.IOException;
import java.io.OutputStream;
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
                Socket socket = new Socket(remoteAddress, Replicator.REPLICATOR_PORT);
                socket.setSoTimeout(30000);
                OutputStream out = socket.getOutputStream();
                neighbor.setSink(socket);
                neighbor.setWaitingForSinkOpen(false);
                log.info("Sink {} is open, configured = {}", remoteAddress, neighbor.isFlagged());
                while ( !ReplicatorSinkPool.instance().shutdown ) {
                    try {
                        ByteBuffer message = neighbor.getNextMessage();
                        byte [] bytes = message.array();
                        if (bytes.length == Node.TRANSACTION_PACKET_SIZE && !socket.isClosed()) {
                            try {
                                out.write(message.array());
                            }
                            catch (IOException e2) {
                                log.error("Error wrting to sink: {}", e2);
                            }
                        }
                    }
                    catch (InterruptedException e) {
                        log.error("Interrupted while waiting for send buffer");
                    }
                }
            }
        } catch (Exception e) {
            log.error("Could not create outbound connection to host {} port {}", remoteAddress,
                    Replicator.REPLICATOR_PORT);
            neighbor.setWaitingForSinkOpen(false);
            return;
        }

    }

}
