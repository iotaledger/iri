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
    	try {
    		Thread.sleep(1000);
    	}
    	catch (InterruptedException e) {
    		log.info("Interrupted");
    	}

        String remoteAddress = neighbor.getAddress().getAddress().getHostAddress();
        
        try {
            Socket socket = null;
            synchronized (neighbor) {             
                if (neighbor.getSink() == null) {
                    socket = new Socket();
                    socket.setSoTimeout(30000);
                    neighbor.setSink(socket);
                }
                else {
                    // Sink already created
                    log.info("Sink {} already created",remoteAddress);
                    return;
                }
            }
            
            if (socket != null) {
                socket.connect(new InetSocketAddress(remoteAddress, Replicator.REPLICATOR_PORT), 30000);
                if (!socket.isClosed()) {
                    OutputStream out = socket.getOutputStream();
                    log.info("----- NETWORK INFO ----- Sink {} is connected, configured = {}", remoteAddress,
                            neighbor.isFlagged());
                    while (!ReplicatorSinkPool.instance().shutdown) {
                        try {
                            ByteBuffer message = neighbor.getNextMessage();
                            if ((neighbor.getSink() != null && neighbor.getSink().isConnected())
                                    && (neighbor.getSource() != null && neighbor.getSource().isConnected())) {
                                byte[] bytes = message.array();
                                if (bytes.length == Node.TRANSACTION_PACKET_SIZE) {
                                    boolean resend;
                                    do {
                                        resend = false;
                                        try {
                                            out.write(message.array());
                                            out.flush();
                                        } catch (IOException e2) {
                                            if (!neighbor.getSink().isClosed() && neighbor.getSink().isConnected()) {
                                                out.close();
                                                out = neighbor.getSink().getOutputStream();
                                                resend = true;
                                            } else {
                                                log.info("----- NETWORK INFO ----- Sink {} thread terminating", remoteAddress);
                                                return;
                                            }
                                        }
                                    } while (resend);
                                }
                            }
                        } catch (InterruptedException e) {
                            log.error("Interrupted while waiting for send buffer");
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("***** NETWORK ALERT ***** Could not create outbound connection to host {} port {} reason: {}", remoteAddress, Replicator.REPLICATOR_PORT,e.getMessage());
            synchronized (neighbor) {
                neighbor.setSource(null);
                neighbor.setSink(null);
            }
            return;
        }

    }

}
