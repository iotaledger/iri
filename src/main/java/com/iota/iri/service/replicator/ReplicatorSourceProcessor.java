package com.iota.iri.service.replicator;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Neighbor;
import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.Hash;
import com.iota.iri.service.Node;
import com.iota.iri.service.viewModels.TransactionViewModel;

class ReplicatorSourceProcessor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ReplicatorSourceProcessor.class);

    private final Socket connection;

    private final static int TRANSACTION_PACKET_SIZE = Node.TRANSACTION_PACKET_SIZE;
    private final boolean shutdown = false;
    
    private boolean existingNeighbor;
    
    private Neighbor neighbor;
    


    public ReplicatorSourceProcessor(Socket connection) {
        this.connection = connection;
    }
    
    private final DatagramPacket sendingPacket = new DatagramPacket(new byte[TRANSACTION_PACKET_SIZE], TRANSACTION_PACKET_SIZE);

    @Override
    public void run() {
        int count;
        byte[] data = new byte[2000];
        int offset = 0;
        boolean isNew;
        boolean finallyClose = true;

        try {
            final Curl curl = new Curl();
            final int[] receivedTransactionTrits = new int[TransactionViewModel.TRINARY_SIZE];
            final byte[] requestedTransaction = new byte[Hash.SIZE_IN_BYTES];

            SocketAddress address = connection.getRemoteSocketAddress();
            InetSocketAddress inet_socket_address = (InetSocketAddress) address;

            existingNeighbor = false;
            List<Neighbor> neighbors = Node.instance().getNeighbors();            
            neighbors.forEach(n -> {
                if (n.isTcpip()) {
                    String hisAddress = inet_socket_address.getAddress().getHostAddress();
                    if (n.getHostAddress().equals(hisAddress)) {
                        existingNeighbor = true;
                        neighbor = n;
                    }
                }
            });
            
            if (!existingNeighbor) {
                if (!Configuration.booling(Configuration.DefaultConfSettings.TESTNET)) {
                    String sb = "***** NETWORK ALERT ***** Got connected from unknown neighbor tcp://"
                            + inet_socket_address.getHostName() + ":" + String.valueOf(inet_socket_address.getPort())
                            + " (" + inet_socket_address.getAddress().getHostAddress() + ") - closing connection";
                    log.info(sb);
                    connection.getInputStream().close();
                    connection.shutdownInput();
                    connection.shutdownOutput();
                    connection.close();
                    return;
                } else {
                    // TODO This code is only for testnet/stresstest - remove for mainnet!
                    Neighbor fresh_neighbor = new Neighbor(inet_socket_address, true, false);
                    Node.instance().getNeighbors().add(fresh_neighbor);
                    neighbor = fresh_neighbor;
                }
            }
            
            if ( neighbor.getSource() != null ) {
                log.info("Source {} already connected", inet_socket_address.getAddress().getHostAddress());
                finallyClose = false;
                return;
            }
            neighbor.setTcpip(true);
            neighbor.setSource(connection);
            
            if (neighbor.getSink() == null) {
                log.info("Creating sink for {}", neighbor.getHostAddress());
                ReplicatorSinkPool.instance().createSink(neighbor);
            }

            InputStream stream = connection.getInputStream();
            
            if (connection.isConnected()) {
                log.info("----- NETWORK INFO ----- Source {} is connected", inet_socket_address.getAddress().getHostAddress());
            }
            
            connection.setSoTimeout(0);  // infinite timeout - blocking read


            while (!shutdown) {
                boolean readError = false;

                while (((count = stream.read(data, offset, TRANSACTION_PACKET_SIZE - offset)) != -1) && (offset < TRANSACTION_PACKET_SIZE)) {
                    offset += count;
                }
              
                if ( count == -1 || connection.isClosed() ) {
                    break;
                }
                
                offset = 0;

                try {
                    Node.instance().processReceivedData(data, address, "tcp", curl, receivedTransactionTrits, requestedTransaction);
                }
                  catch (IllegalStateException e) {
                    log.error("Queue is full for neighbor IP {}",inet_socket_address.getAddress().getHostAddress());
                } catch (final RuntimeException e) {
                    log.error("Transdaction processing runtime exception ",e);
                    neighbor.incInvalidTransactions();
                } catch (Exception e) {
                    log.info("Transdaction processing exception " + e.getMessage());
                    log.error("Transdaction processing exception ",e);
                }
            }
        } catch (IOException e) {
            log.error("***** NETWORK ALERT ***** TCP connection reset by neighbor {}, source closed, {}", neighbor.getHostAddress(), e.getMessage());
            ReplicatorSinkPool.instance().shutdownSink(neighbor);
        } finally {
            if (neighbor != null) {
                if (finallyClose) {
                    ReplicatorSinkPool.instance().shutdownSink(neighbor);
                    neighbor.setSource(null);
                    neighbor.setSink(null);
                }                   
            }
        }
    }
}
