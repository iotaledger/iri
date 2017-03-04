package com.iota.iri.service.storage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Neighbor;

public class ReplicatorSinkPool {
    
    private static final Logger log = LoggerFactory.getLogger(ReplicatorSinkPool.class);
    
    private ExecutorService sinkPool;

    public void init() throws IOException {
        sinkPool = Executors.newFixedThreadPool(ReplicatorSourcePool.NUM_THREADS);
        Replicator.instance().getNeighbors().forEach(neighbor -> {
            if (neighbor.getSink() == null) {
                createSink(neighbor);
            }
        });
        
    }

    public void createSink(Neighbor neighbor) {
        Runnable proc = new ReplicatorSinkProcessor( neighbor );
        sinkPool.submit(proc);
    }
    
    public void shutdown() {
        // TODO
    }

    private static ReplicatorSinkPool instance = new ReplicatorSinkPool();

    private ReplicatorSinkPool() {
    }

    public static ReplicatorSinkPool instance() {
        return instance;
    }

}
