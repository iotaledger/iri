package com.iota.iri.service.replicator;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Neighbor;
import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.service.Node;

class ReplicatorSinkProcessor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ReplicatorSinkProcessor.class);

    private final Neighbor neighbor;

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
                socket.connect(new InetSocketAddress(remoteAddress, neighbor.getTcpPort()), 30000);
                if (!socket.isClosed() && socket.isConnected()) {
                    OutputStream out = socket.getOutputStream();
                    log.info("----- NETWORK INFO ----- Sink {} is connected", remoteAddress);
                    while (!ReplicatorSinkPool.instance().shutdown) {
                        try {
                            ByteBuffer message = neighbor.getNextMessage();
                            Socket s = neighbor.getSink();
                            if (message == null && (neighbor.getSink().isClosed() || !neighbor.getSink().isConnected())) {
                                log.info("----- NETWORK INFO ----- Sink {} got disconnected", remoteAddress);
                                return;
                            } else {
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
                                                if (!neighbor.getSink().isClosed()
                                                        && neighbor.getSink().isConnected()) {
                                                    out.close();
                                                    out = neighbor.getSink().getOutputStream();
                                                    resend = true;
                                                } else {
                                                    log.info("----- NETWORK INFO ----- Sink {} thread terminating",
                                                            remoteAddress);
                                                    return;
                                                }
                                            }
                                        } while (resend);
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
            log.error("***** NETWORK ALERT ***** No sink to host {} port {}, reason: {}", 
                    remoteAddress, neighbor.getTcpPort(), e.getMessage());
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
