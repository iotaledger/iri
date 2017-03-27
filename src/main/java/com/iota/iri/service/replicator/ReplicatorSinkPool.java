package com.iota.iri.service.replicator;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.iota.iri.service.viewModels.TransactionRequester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Neighbor;
import com.iota.iri.service.Node;
import com.iota.iri.service.viewModels.TransactionViewModel;

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
            // wait until list is populated
            int loopcnt = 10;
            while ((loopcnt-- > 0) && neighbors.size() == 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log.error("Interrupted");
                }
            }
            neighbors.forEach(n -> {
                if (n.isTcpip() && n.isFlagged()) {
                    createSink(n);
                }
            });
        }
        
        while (true) {
            // Restart attempt for neighbors that are in the configuration.
            try {                
                Thread.sleep(30000);
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
                    log.info("Sink {} closed", neighbor.getHostAddress());
                } catch (IOException e) {
                    // TODO
                }
            }
        }
        neighbor.setSink(null);
    }
    
    public void broadcast(TransactionViewModel transaction) {
        if (transaction != null) {
            List<Neighbor> neighbors = Node.instance().getNeighbors();
            if (neighbors != null) {          
                neighbors.forEach(neighbor -> {
                    //if ( (neighbor == null) || (neighbor.getSink() != n.getSink()) ) {
                    {
                        if (neighbor.isTcpip() && (neighbor.getSink() != null) && !neighbor.getSink().isClosed()) {
                            try {
                                synchronized (sendingPacket) {
                                    System.arraycopy(transaction.getBytes(), 0, sendingPacket.getData(), 0,
                                            TransactionViewModel.SIZE);
                                    TransactionRequester.transactionToRequest(sendingPacket.getData(),
                                            TransactionViewModel.SIZE);
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
    
    public void shutdown() throws InterruptedException {
        shutdown = true;
        sinkPool.shutdown();
        sinkPool.awaitTermination(6, TimeUnit.SECONDS);
    }

    private static ReplicatorSinkPool instance = new ReplicatorSinkPool();

    private ReplicatorSinkPool() {
    }

    public static ReplicatorSinkPool instance() {
        return instance;
    }

}
