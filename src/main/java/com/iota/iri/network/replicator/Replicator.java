package com.iota.iri.network.replicator;

import com.iota.iri.conf.NodeConfig;
import com.iota.iri.network.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Replicator {
    
    public static final int NUM_THREADS = 32;
    
    private static final Logger log = LoggerFactory.getLogger(Replicator.class);
    private final ReplicatorSinkPool replicatorSinkPool;
    private final int port;
    private ReplicatorSourcePool replicatorSourcePool;

    public Replicator(Node node, NodeConfig configuration) {
        this.port = configuration.getTcpReceiverPort();
        replicatorSinkPool = new ReplicatorSinkPool(node, port, configuration.getTransactionPacketSize());
        replicatorSourcePool = new ReplicatorSourcePool(replicatorSinkPool, node, configuration.getMaxPeers(),
                configuration.isTestnet());
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
