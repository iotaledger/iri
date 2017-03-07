package com.iota.iri.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Neighbor;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.Transaction;
import com.iota.iri.service.Node;
import com.iota.iri.utils.Converter;

public class ReplicatorSourceProcessor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ReplicatorSourceProcessor.class);

    private Socket connection;

    final static int TRANSACTION_PACKET_SIZE = Node.TRANSACTION_PACKET_SIZE;
    private volatile boolean shutdown = false;
    
    private boolean existingNeighbor;
    
    private Neighbor neighbor;

    public ReplicatorSourceProcessor(Socket connection) {
        this.connection = connection;
    }
    
    final int[] receivedTransactionTrits = new int[Transaction.TRINARY_SIZE];
    final byte[] requestedTransaction = new byte[Transaction.HASH_SIZE];
    
    private final DatagramPacket sendingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE], TRANSACTION_PACKET_SIZE);

    @Override
    public void run() {
        int count;
        byte[] data = new byte[2000];
        int offset = 0;        

        try {
            final Curl curl = new Curl();
            
            SocketAddress address = connection.getRemoteSocketAddress();
            InetSocketAddress inet_socket_address = (InetSocketAddress) address;
            InetSocketAddress inet_socket_address_normalized = new InetSocketAddress(inet_socket_address.getAddress(),Replicator.REPLICATOR_PORT);
            long pointer;
            
            existingNeighbor = false;
            List<Neighbor> neighbors = Node.instance().getNeighbors();            
            neighbors.forEach(n -> {
                if (n.isTcpip() && n.getAddress().equals(inet_socket_address_normalized)) {
                    existingNeighbor = true;
                    neighbor = n;
                }
            });
            
            Neighbor fresh_neighbor = new Neighbor(inet_socket_address_normalized, true, false);
            if (!existingNeighbor) {
                StringBuffer sb = new StringBuffer(80);
                sb.append("***** NETWORK ALERT ***** Got connected from unknown neighbor tcp://")
                    .append(inet_socket_address.getHostName())
                    .append(":")
                    .append(String.valueOf(inet_socket_address.getPort()))
                    .append(" (")
                    .append(inet_socket_address.getAddress().getHostAddress())
                    .append(") - closing connection");
                log.info(sb.toString());
                connection.close();
                return;
                /* -- This is possible code if tethering is disabled 
                Node.instance().getNeighbors().add(fresh_neighbor);
                neighbor = fresh_neighbor;
                */
            }
            neighbor.setTcpip(true);
            neighbor.setSource(connection);
            
            if (neighbor.getSink() == null && !neighbor.isWaitingForSinkOpen() ) {
                ReplicatorSinkPool.instance().createSink(neighbor);
            }
            
            InputStream stream = connection.getInputStream();
            log.info("----- NETWORK INFO ----- Source {} open, configured = {}", inet_socket_address.getAddress().getHostAddress(), neighbor.isFlagged());
            
            while (!shutdown) {
                while (((count = stream.read(data, offset, TRANSACTION_PACKET_SIZE - offset)) != -1)
                        && (offset < TRANSACTION_PACKET_SIZE)) {
                    offset += count;
                }
                if (count == -1)
                    break;
              
                offset = 0;
                                
                try {
                    neighbor.incAllTransactions();
                    final Transaction receivedTransaction = new Transaction(data, receivedTransactionTrits, curl);
                    long timestamp = (int) Converter.longValue(receivedTransaction.trits(), Transaction.TIMESTAMP_TRINARY_OFFSET, 27);
                    if (timestamp > Node.TIMESTAMP_THRESHOLD) {
                        if ((pointer = StorageTransactions.instance().storeTransaction(receivedTransaction.hash, receivedTransaction, false)) != 0L) {                         
                            StorageTransactions.instance().setArrivalTime(pointer, System.currentTimeMillis() / 1000L);
                            neighbor.incNewTransactions();                         
                            Node.instance().broadcast(receivedTransaction); // the UDP path
                            ReplicatorSinkPool.instance().broadcast(receivedTransaction, neighbor); // the TCP path
                        }

                        long transactionPointer = 0L;
                        System.arraycopy(data, Transaction.SIZE, requestedTransaction, 0, Transaction.HASH_SIZE);

                        if (!Arrays.equals(requestedTransaction, Transaction.NULL_TRANSACTION_HASH_BYTES)) {
                            transactionPointer = StorageTransactions.instance().transactionPointer(requestedTransaction);

                            if (transactionPointer != 0L && transactionPointer > (Storage.CELLS_OFFSET - Storage.SUPER_GROUPS_OFFSET)) {
                                synchronized (sendingPacket) {
                                    System.arraycopy( StorageTransactions.instance().loadTransaction(transactionPointer).bytes, 0, sendingPacket.getData(), 0, Transaction.SIZE);
                                    StorageScratchpad.instance().transactionToRequest(sendingPacket.getData(), Transaction.SIZE);
                                    neighbor.send(sendingPacket);
                                }
                            }
                        }
                    }
                } catch (final RuntimeException e) {
                    log.error("Received an Invalid Transaction. Dropping it...");
                    neighbor.incInvalidTransactions();
                }

            }
        } catch (IOException e) {
            log.error("***** NETWORK ALERT ***** TCP onnection reset by neighbor {}, source closed, {}", neighbor.getAddress().getAddress().getHostAddress(), e.getMessage());
            ReplicatorSinkPool.instance().shutdownSink(neighbor);
        } finally {
            if (neighbor != null) {
                neighbor.setSource(null);
                neighbor.setWaitingForSinkOpen(false);
                neighbor.setSink(null);
                ReplicatorSinkPool.instance().shutdownSink(neighbor);
            }
        }
    }
}
