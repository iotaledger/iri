package com.iota.iri.network.replicator;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;

public class ReplicatorSourcePool implements Runnable {

    private volatile boolean shutdown = false;

    private static final Logger log = LoggerFactory.getLogger(ReplicatorSourcePool.class);
    private ExecutorService pool;
    private int port;

    @Override
    public void run() {
        ExecutorService pool;
        ServerSocket server = null;
        pool = Executors.newFixedThreadPool(Replicator.NUM_THREADS);
        this.pool = pool;
        try {
            server = new ServerSocket(port);
            log.info("TCP replicator is accepting connections on tcp port " + server.getLocalPort());
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
            log.error("***** NETWORK ALERT ***** Cannot create server socket on port {}, {}", Configuration.integer(DefaultConfSettings.TANGLE_RECEIVER_PORT_TCP), e.getMessage());
        } finally {
            if (server != null) {
                try {
                    server.close();
                }
                catch (Exception e) {
                    // don't care.
                }
            }
        }
    }

    public void shutdown() throws InterruptedException {
        shutdown = true;
        //notify();
        pool.shutdown();
        pool.awaitTermination(6, TimeUnit.SECONDS);
    }

    private static final ReplicatorSourcePool instance = new ReplicatorSourcePool();

    private ReplicatorSourcePool() {
    }

    public static ReplicatorSourcePool instance() {
        return instance;
    }

    public static ReplicatorSourcePool init(int port) {
        instance.port = port;
        return instance;
    }

}
