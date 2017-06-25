package com.iota.iri.network.replicator;

import com.iota.iri.network.Neighbor;
import com.iota.iri.network.Node;
import com.iota.iri.network.TCPNeighbor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ReplicatorSinkPool  implements Runnable {
    
    private static final Logger log = LoggerFactory.getLogger(ReplicatorSinkPool.class);
    private final int port;
    private final Node node;

    private ExecutorService sinkPool;
    
    public boolean shutdown = false;

    public final static int PORT_BYTES = 10;

    public ReplicatorSinkPool(Node node, int port) {
        this.node = node;
        this.port = port;
    }

    @Override
    public void run() {
        
        sinkPool = Executors.newFixedThreadPool(Replicator.NUM_THREADS);
        {           
            List<Neighbor> neighbors = node.getNeighbors();
            // wait until list is populated
            int loopcnt = 10;
            while ((loopcnt-- > 0) && neighbors.size() == 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log.error("Interrupted");
                }
            }
            neighbors.stream().filter(n -> n instanceof TCPNeighbor && n.isFlagged())
                    .map(n -> ((TCPNeighbor) n))
                    .forEach(this::createSink);
        }
        
        while (!Thread.interrupted()) {
            // Restart attempt for neighbors that are in the configuration.
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                log.debug("Interrupted: ", e);
            }
            List<Neighbor> neighbors = node.getNeighbors();
            neighbors.stream()
                    .filter(n -> n instanceof TCPNeighbor && n.isFlagged())
                    .map(n -> ((TCPNeighbor) n))
                    .filter(n -> n.getSink() == null)
                    .forEach(this::createSink);
        }
    }
    
    public void createSink(TCPNeighbor neighbor) {
        Runnable proc = new ReplicatorSinkProcessor( neighbor, this, port);
        sinkPool.submit(proc);
    }
    
    public void shutdownSink(TCPNeighbor neighbor) {
        Socket socket = neighbor.getSink();
        if (socket != null) {
            if (!socket.isClosed()) {
                try {
                    socket.close();
                    log.info("Sink {} closed", neighbor.getHostAddress());
                } catch (IOException e) {
                    // TODO
                }
            }
        }
        neighbor.setSink(null);
    }

    /*
    public void broadcast(TransactionViewModel transaction) {
        if (transaction != null) {
            List<Neighbor> neighbors = instance.node.getNeighbors();
            if (neighbors != null) {
                neighbors.stream().filter(n -> n instanceof TCPNeighbor)
                        .map(n -> ((TCPNeighbor) n))
                        .filter(n -> n.getSink() != null && !n.getSink().isClosed())
                        .forEach(neighbor -> {
                            try {
                                Node.sendPacket(sendingPacket, transaction, neighbor);
                            } catch (final Exception e) {
                                // ignore
                            }
                        });
            }
        }
    }
    */

    public void shutdown() throws InterruptedException {
        shutdown = true;
        sinkPool.shutdown();
        sinkPool.awaitTermination(6, TimeUnit.SECONDS);
    }
}
