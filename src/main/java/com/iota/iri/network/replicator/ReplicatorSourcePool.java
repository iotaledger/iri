package com.iota.iri.network.replicator;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.iota.iri.network.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplicatorSourcePool implements Runnable {

    private final ReplicatorSinkPool replicatorSinkPool;
    private final Node node;
    private final int maxPeers;
    private final boolean testnet;
    private volatile boolean shutdown = false;

    private static final Logger log = LoggerFactory.getLogger(ReplicatorSourcePool.class);
    private ExecutorService pool;
    private int port;

    public ReplicatorSourcePool(final ReplicatorSinkPool replicatorSinkPool,
                                final Node node,
                                final int maxPeers,
                                final boolean testnet) {
        this.replicatorSinkPool = replicatorSinkPool;
        this.node = node;
        this.maxPeers = maxPeers;
        this.testnet = testnet;
    }

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
                    Runnable proc = new ReplicatorSourceProcessor( replicatorSinkPool, request, node, maxPeers, testnet);
                    pool.submit(proc);
                } catch (IOException ex) {
                    log.error("Error accepting connection", ex);
                }
            }
            log.info("ReplicatorSinkPool shutting down");
        } catch (IOException e) {
            log.error("***** NETWORK ALERT ***** Cannot create server socket on port {}, {}", port, e.getMessage());
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

    public ReplicatorSourcePool init(int port) {
        this.port = port;
        return this;
    }

}
