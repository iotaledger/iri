package com.iota.iri.service.replicator;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplicatorSourcePool implements Runnable {
    
    private ServerSocket server;
    
    private volatile boolean shutdown = false;

    private static final Logger log = LoggerFactory.getLogger(ReplicatorSourcePool.class);
    private ExecutorService pool;

    @Override
    public void run() {
        ExecutorService pool;
        pool = Executors.newFixedThreadPool(Replicator.NUM_THREADS);
        this.pool = pool;
        try {
            server = new ServerSocket(Replicator.REPLICATOR_PORT); 
            log.info("Replicator is accepting connections on port " + server.getLocalPort());
            while (!shutdown) {
                try {
                    Socket request = server.accept();
                    request.setSoLinger(true, 0);
                    Runnable proc = new ReplicatorSourceProcessor( request );
                    pool.submit(proc);
                } catch (IOException ex) {
                    log.error("Error accepting connection", ex);
                }
            }
            log.info("ReplicatorSinkPool shutting down");
        } catch (IOException e) {
            log.error("***** NETWORK ALERT ***** Cannot create server socket on port {}, {}", Replicator.REPLICATOR_PORT, e.getMessage());
        }
    }

    public void shutdown() throws InterruptedException {
        shutdown = true;
        //notify();
        pool.shutdown();
        pool.awaitTermination(6, TimeUnit.SECONDS);
    }

    private static ReplicatorSourcePool instance = new ReplicatorSourcePool();

    private ReplicatorSourcePool() {
    }

    public static ReplicatorSourcePool instance() {
        return instance;
    }

}
