package com.iota.iri.service.storage;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Neighbor;
import com.iota.iri.service.Node;

public class ReplicatorSinkPool  implements Runnable {
    
    private static final Logger log = LoggerFactory.getLogger(ReplicatorSinkPool.class);
    
    private ExecutorService sinkPool;

    @Override
    public void run() {
        
        sinkPool = Executors.newFixedThreadPool(Replicator.NUM_THREADS);
        {
            List<Neighbor> neighbors = Node.instance().getNeighbors();
            neighbors.forEach(n -> {
                if (n.isTcpip() && n.isFlagged()) {
                    createSink(n);
                }
            });
        }
        
        while (true) {
            try {                
                Thread.sleep(5000);
                List<Neighbor> neighbors = Node.instance().getNeighbors();
                neighbors.forEach(n -> {
                    if (n.isTcpip()) {
                        if ( n.isTcpip() && n.isFlagged() && (n.getSink() == null) ) {
                            createSink(n);
                        }
                    }
                });
            } catch (InterruptedException e) {
                log.error("Interrupted");
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
