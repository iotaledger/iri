package com.iota.iri.service.storage;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Neighbor;

public class ReplicatorSinkPool  implements Runnable {
    
    private static final Logger log = LoggerFactory.getLogger(ReplicatorSinkPool.class);
    
    private ExecutorService sinkPool;

    @Override
    public void run() {
        
        sinkPool = Executors.newFixedThreadPool(Replicator.NUM_THREADS);
        Replicator.instance().getNeighbors().forEach(neighbor -> {
            if (neighbor.getSink() == null) {
                createSink(neighbor);
            }
        });
        
        while (true) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                log.error("Interreupted");
            }
        }        
    }
    
    public void createSink(Neighbor neighbor) {
        if (neighbor.getSink() != null) return;
        Runnable proc = new ReplicatorSinkProcessor( neighbor );
        sinkPool.submit(proc);
    }
    
    public void shutdownSink(Neighbor neighbor) {
        Socket socket = neighbor.getSink();
        if (socket != null) {
            if (!socket.isClosed()) {
                try {
                    socket.close();
                    log.info("Sink {} closed", neighbor.getAddress().getAddress().getHostAddress());
                } catch (IOException e) {
                }
            }
        }
        neighbor.setSink(null);
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
