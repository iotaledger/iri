package com.iota.iri.service.storage;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Neighbor;
import com.iota.iri.model.Transaction;
import com.iota.iri.service.Node;

public class ReplicatorSinkPool  implements Runnable {
    
    private static final Logger log = LoggerFactory.getLogger(ReplicatorSinkPool.class);
    
    private ExecutorService sinkPool;
    
    public boolean shutdown = false;

    private final DatagramPacket sendingPacket = new DatagramPacket(new byte[Node.TRANSACTION_PACKET_SIZE], Node.TRANSACTION_PACKET_SIZE);
    
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
            // Restart attempt for neighbors that are in the configuration.
            try {                
                Thread.sleep(10000);
                List<Neighbor> neighbors = Node.instance().getNeighbors();
                neighbors.forEach(n -> {
                    if (n.isTcpip() && n.isFlagged() && n.getSink() == null) {
                        createSink(n);
                    }
                });
            } catch (InterruptedException e) {
                log.error("Interrupted");
            }
        }        
    }
    
    public void createSink(Neighbor neighbor) {        
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
                    // TODO
                }
            }
        }
        neighbor.setSink(null);
    }
    
    public void broadcast(Transaction transaction, Neighbor neighbor) {
        if (transaction != null) {
            List<Neighbor> neighbors = Node.instance().getNeighbors();
            if (neighbors != null) {          
                neighbors.forEach(n -> {
                    //if ( (neighbor == null) || (neighbor.getSink() != n.getSink()) ) {
                    {
                        if (n.isTcpip() && (n.getSink() != null) && !n.getSink().isConnected()) {

                            try {
                                synchronized (sendingPacket) {
                                    System.arraycopy(transaction.bytes, 0, sendingPacket.getData(), 0,
                                            Transaction.SIZE);
                                    StorageScratchpad.instance().transactionToRequest(sendingPacket.getData(),
                                            Transaction.SIZE);
                                    neighbor.send(sendingPacket);
                                }
                            } catch (final Exception e) {
                                // ignore
                            }
                        }
                    }
                });
            }
        }
    }
    
    public void shutdown() {
        shutdown = true;
    }

    private static ReplicatorSinkPool instance = new ReplicatorSinkPool();

    private ReplicatorSinkPool() {
    }

    public static ReplicatorSinkPool instance() {
        return instance;
    }

}
