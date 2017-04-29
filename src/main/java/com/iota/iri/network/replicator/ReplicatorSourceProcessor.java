package com.iota.iri.network.replicator;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.List;

import com.iota.iri.network.TCPNeighbor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.network.Neighbor;
import com.iota.iri.conf.Configuration;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.Hash;
import com.iota.iri.network.Node;
import com.iota.iri.controllers.TransactionViewModel;

class ReplicatorSourceProcessor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ReplicatorSourceProcessor.class);

    private final Socket connection;

    private final static int TRANSACTION_PACKET_SIZE = Node.TRANSACTION_PACKET_SIZE;
    private final boolean shutdown = false;
    
    private boolean existingNeighbor;
    
    private TCPNeighbor neighbor;
    


    public ReplicatorSourceProcessor(Socket connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        int count;
        byte[] data = new byte[2000];
        int offset = 0;
        //boolean isNew;
        boolean finallyClose = true;

        try {
            final Curl curl = new Curl();
            final int[] receivedTransactionTrits = new int[TransactionViewModel.TRINARY_SIZE];
            final byte[] requestedTransaction = new byte[Hash.SIZE_IN_BYTES];

            SocketAddress address = connection.getRemoteSocketAddress();
            InetSocketAddress inet_socket_address = (InetSocketAddress) address;

            existingNeighbor = false;
            List<Neighbor> neighbors = Node.instance().getNeighbors();            
            neighbors.stream().filter(n -> n instanceof TCPNeighbor)
                    .map(n -> ((TCPNeighbor) n))
                    .forEach(n -> {
                        String hisAddress = inet_socket_address.getAddress().getHostAddress();
                        if (n.getHostAddress().equals(hisAddress)) {
                            existingNeighbor = true;
                            neighbor = n;
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
                    final TCPNeighbor fresh_neighbor = new TCPNeighbor(inet_socket_address, false);
                    Node.instance().getNeighbors().add(fresh_neighbor);
                    neighbor = fresh_neighbor;
                }
            }
            
            if ( neighbor.getSource() != null ) {
                log.info("Source {} already connected", inet_socket_address.getAddress().getHostAddress());
                finallyClose = false;
                return;
            }
            neighbor.setSource(connection);
            
            // Read neighbors tcp listener port number.
            InputStream stream = connection.getInputStream();
            offset = 0;
            while (((count = stream.read(data, offset, ReplicatorSinkPool.PORT_BYTES - offset)) != -1) && (offset < ReplicatorSinkPool.PORT_BYTES)) {
                offset += count;
            }
          
            if ( count == -1 || connection.isClosed() ) {
                log.error("Did not receive neighbors listener port");
                return;
            }
            
            byte [] pbytes = new byte [10];
            System.arraycopy(data, 0, pbytes, 0, ReplicatorSinkPool.PORT_BYTES);
            neighbor.setTcpPort((int)Long.parseLong(new String(pbytes)));
            
            if (neighbor.getSink() == null) {
                log.info("Creating sink for {}", neighbor.getHostAddress());
                ReplicatorSinkPool.instance().createSink(neighbor);
            }           
            
            if (connection.isConnected()) {
                log.info("----- NETWORK INFO ----- Source {} is connected", inet_socket_address.getAddress().getHostAddress());
            }
            
            connection.setSoTimeout(0);  // infinite timeout - blocking read

            offset = 0;
            while (!shutdown) {

                while (((count = stream.read(data, offset, TRANSACTION_PACKET_SIZE - offset)) != -1) && (offset < TRANSACTION_PACKET_SIZE)) {
                    offset += count;
                }
              
                if ( count == -1 || connection.isClosed() ) {
                    break;
                }
                
                offset = 0;

                try {
                    Node.instance().processReceivedData(data, address, "tcp", curl);
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
                    if (!neighbor.isFlagged() ) {
                       Node.instance().getNeighbors().remove(neighbor);   
                    }
                }                   
            }
        }
    }
}
