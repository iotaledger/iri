package com.iota.iri.network.replicator;

import com.iota.iri.conf.NodeConfig;
import com.iota.iri.network.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class manages a set of Source and Sink pool workers {@link ReplicatorSourceProcessor}
 * and {@link ReplicatorSinkProcessor}. The workers are managed by the Pool manager threads
 * {@link ReplicatorSourcePool} and {@link ReplicatorSinkPool}
 * <br>
 * A **Sink** is basically a received end point for TCP where all  the peers send
 * trnasactions to. Since there is only one global endpoint for all the peers, we need
 * only a single thread to manage all the incoming messages. 
 * <br>
 * A **Source** is a single peer which sends packets to us, therefore we need multiple worker
 * threads to manage sending transaction to multiple peers. 
 * 
 */
 
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
