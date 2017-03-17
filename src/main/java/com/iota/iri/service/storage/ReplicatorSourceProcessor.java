package com.iota.iri.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
import com.iota.iri.service.ScratchpadViewModel;
import com.iota.iri.service.tangle.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Neighbor;
import com.iota.iri.hash.Curl;
import com.iota.iri.service.Node;
import com.iota.iri.service.viewModels.TransactionViewModel;
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
    
    final int[] receivedTransactionTrits = new int[TransactionViewModel.TRINARY_SIZE];
    final byte[] requestedTransaction = new byte[Hash.SIZE_IN_BYTES];
    
    private final DatagramPacket sendingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE], TRANSACTION_PACKET_SIZE);

    @Override
    public void run() {
        int count;
        byte[] data = new byte[2000];
        int offset = 0;
        long beginning, now;
        boolean isNew;

        try {
            final Curl curl = new Curl();
            
            SocketAddress address = connection.getRemoteSocketAddress();
            InetSocketAddress inet_socket_address = (InetSocketAddress) address;
            InetSocketAddress inet_socket_address_normalized = new InetSocketAddress(inet_socket_address.getAddress(),Replicator.REPLICATOR_PORT);

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
            
            ReplicatorSinkPool.instance().createSink(neighbor);

            InputStream stream = connection.getInputStream();
            log.info("----- NETWORK INFO ----- Source {} is connected, configured = {}", inet_socket_address.getAddress().getHostAddress(), neighbor.isFlagged());
            
            connection.setSoTimeout(0);
            
            while (!shutdown) {
                boolean readError = false;

                while (((count = stream.read(data, offset, TRANSACTION_PACKET_SIZE - offset)) != -1) && (offset < TRANSACTION_PACKET_SIZE)) {
                    offset += count;
                }
              
                if ( count == -1 || connection.isClosed() ) {
                    readError = true;
                    break;
                }
                
                offset = 0;

                if (!readError) {
                    try {
                        neighbor.incAllTransactions();
                        beginning = System.nanoTime();
                        final TransactionViewModel receivedTransactionViewModel = new TransactionViewModel(data, receivedTransactionTrits, curl);
                        log.info("TransactionVM creation time: " + ((now = System.nanoTime()) - beginning)/1000+ " us");
                        long timestamp = (int) Converter.longValue(receivedTransactionViewModel.trits(), TransactionViewModel.TIMESTAMP_TRINARY_OFFSET, 27);
                        if (timestamp == 0 || timestamp > Node.TIMESTAMP_THRESHOLD) {
                            beginning = System.nanoTime();
                            isNew = !receivedTransactionViewModel.store().get();
                            if(isNew) {
                                now = System.nanoTime();
                                log.info("TransactionVM save time: " + ((now) - beginning)/1000 + " us");
                                receivedTransactionViewModel.setArrivalTime(System.currentTimeMillis() / 1000L);
                                receivedTransactionViewModel.update("arrivalTime");
                                neighbor.incNewTransactions();
                                // The UDP transport route
                                //assert(Arrays.equals(receivedTransactionViewModel.getHash(), new TransactionViewModel(TransactionViewModel.fromHash(receivedTransactionViewModel.getHash()).trits()).getHash()));
                                Node.instance().broadcast(receivedTransactionViewModel);
                                // The TCP transport route
                                ReplicatorSinkPool.instance().broadcast(receivedTransactionViewModel, neighbor);                        
                            }

                            long transactionPointer = 0L;
                            System.arraycopy(data, TransactionViewModel.SIZE, requestedTransaction, 0, Hash.SIZE_IN_BYTES);

                            if (!Arrays.equals(requestedTransaction, TransactionViewModel.NULL_TRANSACTION_HASH_BYTES)) {
                                //beginning = System.nanoTime();
                                TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(requestedTransaction);
                                //log.info("DB Retrieve time: " + ((now = System.nanoTime()) - beginning)/1000 + " us");
                                if(!Arrays.equals(transactionViewModel.getBytes(), TransactionViewModel.NULL_TRANSACTION_BYTES)) {
                                    synchronized (sendingPacket) {
                                        System.arraycopy( transactionViewModel.getBytes(),
                                                0, sendingPacket.getData(), 0, TransactionViewModel.SIZE);
                                        ScratchpadViewModel.instance().transactionToRequest(sendingPacket.getData(), TransactionViewModel.SIZE);
                                        neighbor.send(sendingPacket);
                                    }
                                }
                            }
                        }
                    } catch (final RuntimeException e) {
                        log.error("Transdaction processing runtime exception ",e);
                        neighbor.incInvalidTransactions();
                    } catch (InterruptedException e) {
                        log.error("Interrupted");
                    } catch (ExecutionException e) {
                        log.error("Transdaction propagation exception ",e);
                    } catch (Exception e) {
                        log.error("Transdaction processing exception ",e);
                    }
                }
                else {
                    log.error("***** NETWORK ALERT ***** TCP connection reset by network {}, source closed", neighbor.getAddress().getAddress().getHostAddress());
                    break;
                }
            }
        } catch (IOException e) {
            log.error("***** NETWORK ALERT ***** TCP connection reset by neighbor {}, source closed, {}", neighbor.getAddress().getAddress().getHostAddress(), e.getMessage());
            ReplicatorSinkPool.instance().shutdownSink(neighbor);
        } finally {
            if (neighbor != null) {
                ReplicatorSinkPool.instance().shutdownSink(neighbor);
                neighbor.setSource(null);
                neighbor.setSink(null);                
            }
        }
    }
}
