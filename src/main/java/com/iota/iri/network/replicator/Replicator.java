package com.iota.iri.network.replicator;

import com.iota.iri.network.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Replicator {
    
    public static final int NUM_THREADS = 32;
    
    private static final Logger log = LoggerFactory.getLogger(Replicator.class);
    private final ReplicatorSinkPool replicatorSinkPool;
    private final int port;
    private ReplicatorSourcePool replicatorSourcePool;

    public Replicator(final Node node, int port, final int maxPeers, final boolean testnet) {
        this.port = port;
        replicatorSinkPool = new ReplicatorSinkPool(node, port);
        replicatorSourcePool = new ReplicatorSourcePool(replicatorSinkPool, node, maxPeers, testnet);
    }

    public void init() {
        new Thread(replicatorSinkPool).start();
        new Thread(replicatorSourcePool.init(port)).start();
        log.info("Started ReplicatorSourcePool");
    }
    
    public void shutdown() throws InterruptedException {
        // TODO
        replicatorSourcePool.shutdown();
        replicatorSinkPool.shutdown();
    }
    
}
