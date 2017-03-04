package com.iota.iri.service.storage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplicatorSourcePool implements Runnable {

    public static final int NUM_THREADS = 32;

    public static final int REPLICATOR_PORT = 14600;
    
    private ServerSocket server;
    
    private volatile boolean shutdown = false;

    private static final Logger log = LoggerFactory.getLogger(ReplicatorSourcePool.class);

    @Override
    public void run() {
        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);
        try {
            server = new ServerSocket(REPLICATOR_PORT); 
            log.info("Replicator is accepting connections on port " + server.getLocalPort());
            while (!shutdown) {
                try {
                    Socket request = server.accept();
                    Runnable proc = new ReplicatorSourceProcessor( request );
                    pool.submit(proc);
                } catch (IOException ex) {
                    log.error("Error accepting connection", ex);
                }
            }
            log.info("ReplicatorSinkPool shutting down");
        } catch (IOException e) {
            log.error("Error createing Server Socket on port {}", REPLICATOR_PORT);
        }
    }

    public void shutdown() {
        ReplicatorSourcePool.instance().shutdown = true;
        ReplicatorSourcePool.instance().notify();
    }

    private static ReplicatorSourcePool instance = new ReplicatorSourcePool();

    private ReplicatorSourcePool() {
    }

    public static ReplicatorSourcePool instance() {
        return instance;
    }

}
