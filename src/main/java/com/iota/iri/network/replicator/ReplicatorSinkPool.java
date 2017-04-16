package com.iota.iri.network.replicator;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.iota.iri.network.TCPNeighbor;
import com.iota.iri.controllers.TransactionRequester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.network.Neighbor;
import com.iota.iri.network.Node;
import com.iota.iri.controllers.TransactionViewModel;

public class ReplicatorSinkPool  implements Runnable {
    
    private static final Logger log = LoggerFactory.getLogger(ReplicatorSinkPool.class);
    
    private ExecutorService sinkPool;
    
    public boolean shutdown = false;
    
    public final static int PORT_BYTES = 10;

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
            List<Neighbor> neighbors = Node.instance().getNeighbors();
            neighbors.stream()
                    .filter(n -> n instanceof TCPNeighbor && n.isFlagged())
                    .map(n -> ((TCPNeighbor) n))
                    .filter(n -> n.getSink() == null)
                    .forEach(this::createSink);
        }
    }
    
    public void createSink(TCPNeighbor neighbor) {
        Runnable proc = new ReplicatorSinkProcessor( neighbor );
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
    
    public void broadcast(TransactionViewModel transaction) {
        if (transaction != null) {
            List<Neighbor> neighbors = Node.instance().getNeighbors();
            if (neighbors != null) {
                neighbors.stream().filter(n -> n instanceof TCPNeighbor)
                        .map(n -> ((TCPNeighbor) n))
                        .filter(n -> n.getSink() != null && !n.getSink().isClosed())
                        .forEach(neighbor -> {
                            try {
                                synchronized (sendingPacket) {
                                    System.arraycopy(transaction.getBytes(), 0, sendingPacket.getData(), 0,
                                            TransactionViewModel.SIZE);
                                    TransactionRequester.instance().transactionToRequest(sendingPacket.getData(),
                                            TransactionViewModel.SIZE);
                                    neighbor.send(sendingPacket);
                                }
                            } catch (final Exception e) {
                                // ignore
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

    private static final ReplicatorSinkPool instance = new ReplicatorSinkPool();

    private ReplicatorSinkPool() {
    }

    public static ReplicatorSinkPool instance() {
        return instance;
    }

}
