package com.iota.iri.service.replicator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Replicator {
    
    public static final int NUM_THREADS = 32;
    
    private static final Logger log = LoggerFactory.getLogger(Replicator.class);
    
    public void init() {
        
        new Thread(ReplicatorSinkPool.instance()).start();
        new Thread(ReplicatorSourcePool.instance()).start();
        log.info("Started ReplicatorSourcePool");
    }
    
    public void shutdown() {
        // TODO
    }
    
    private static final Replicator instance = new Replicator();

    private Replicator() {
    }

    public static Replicator instance() {
        return instance;
    }

}
