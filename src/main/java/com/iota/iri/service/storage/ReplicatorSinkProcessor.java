package com.iota.iri.service.storage;

import java.net.InetSocketAddress;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Neighbor;

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
                Socket socket = new Socket(remoteAddress, ReplicatorSourcePool.REPLICATOR_PORT);
                neighbor.setSink(socket);
                
                
            }
        } catch (Exception e) {
            log.error("Could not create outbound connection to host {} port {}", remoteAddress,
                    ReplicatorSourcePool.REPLICATOR_PORT);
            return;
        }

    }

}
