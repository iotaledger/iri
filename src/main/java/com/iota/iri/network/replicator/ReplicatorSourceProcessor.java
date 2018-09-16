package com.iota.iri.network.replicator;

import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.conf.TestnetConfig;
import com.iota.iri.network.Neighbor;
import com.iota.iri.network.Node;
import com.iota.iri.network.TCPNeighbor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.List;
import java.util.zip.CRC32;

class ReplicatorSourceProcessor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ReplicatorSourceProcessor.class);

    private final Socket connection;

    private final boolean shutdown = false;
    private final Node node;
    private final int maxPeers;
    private final boolean testnet;
    private final ReplicatorSinkPool replicatorSinkPool;
    private final int packetSize;

    private boolean existingNeighbor;
    
    private TCPNeighbor neighbor;

    public ReplicatorSourceProcessor(final ReplicatorSinkPool replicatorSinkPool,
                                     final Socket connection,
                                     final Node node,
                                     final int maxPeers,
                                     final boolean testnet) {
        this.connection = connection;
        this.node = node;
        this.maxPeers = maxPeers;
        this.testnet = testnet;
        this.replicatorSinkPool = replicatorSinkPool;
        this.packetSize = testnet
                ? TestnetConfig.Defaults.PACKET_SIZE
                : MainnetConfig.Defaults.PACKET_SIZE;
    }

    @Override
    public void run() {
        int count;
        byte[] data = new byte[2000];
        int offset = 0;
        //boolean isNew;
        boolean finallyClose = true;

        try {
            SocketAddress address = connection.getRemoteSocketAddress();
            InetSocketAddress inetSocketAddress = (InetSocketAddress) address;

            existingNeighbor = false;
            List<Neighbor> neighbors = node.getNeighbors();
            neighbors.stream().filter(n -> n instanceof TCPNeighbor)
                    .map(n -> ((TCPNeighbor) n))
                    .forEach(n -> {
                        String hisAddress = inetSocketAddress.getAddress().getHostAddress();
                        if (n.getHostAddress().equals(hisAddress)) {
                            existingNeighbor = true;
                            neighbor = n;
                        }
                    });
            
            if (!existingNeighbor) {
                int maxPeersAllowed = maxPeers;
                if (!testnet || Neighbor.getNumPeers() >= maxPeersAllowed) {
                    String hostAndPort = inetSocketAddress.getHostName() + ":" + String.valueOf(inetSocketAddress.getPort());
                    if (Node.rejectedAddresses.add(inetSocketAddress.getHostName())) {
                        String sb = "***** NETWORK ALERT ***** Got connected from unknown neighbor tcp://"
                            + hostAndPort
                            + " (" + inetSocketAddress.getAddress().getHostAddress() + ") - closing connection";
                        if (testnet && Neighbor.getNumPeers() >= maxPeersAllowed) {
                            sb = sb + (" (max-peers allowed is "+String.valueOf(maxPeersAllowed)+")");
                        }
                        log.info(sb);
                    }
                    connection.getInputStream().close();
                    connection.shutdownInput();
                    connection.shutdownOutput();
                    connection.close();
                    return;
                } else {
                    final TCPNeighbor freshNeighbor = new TCPNeighbor(inetSocketAddress, false);
                    node.getNeighbors().add(freshNeighbor);
                    neighbor = freshNeighbor;
                    Neighbor.incNumPeers();
                }
            }
            
            if ( neighbor.getSource() != null ) {
                log.info("Source {} already connected", inetSocketAddress.getAddress().getHostAddress());
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
                replicatorSinkPool.createSink(neighbor);
            }           
            
            if (connection.isConnected()) {
                log.info("----- NETWORK INFO ----- Source {} is connected", inetSocketAddress.getAddress().getHostAddress());
            }
            
            connection.setSoTimeout(0);  // infinite timeout - blocking read

            offset = 0;
            while (!shutdown && !neighbor.isStopped()) {

                while ( ((count = stream.read(data, offset, (packetSize- offset + ReplicatorSinkProcessor.CRC32_BYTES))) != -1)
                        && (offset < (packetSize + ReplicatorSinkProcessor.CRC32_BYTES))) {
                    offset += count;
                }
              
                if ( count == -1 || connection.isClosed() ) {
                    break;
                }
                
                offset = 0;

                try {
                    CRC32 crc32 = new CRC32();
                    for (int i=0; i<packetSize; i++) {
                        crc32.update(data[i]);
                    }
                    String crc32String = Long.toHexString(crc32.getValue());
                    while (crc32String.length() < ReplicatorSinkProcessor.CRC32_BYTES) {
                        crc32String = "0"+crc32String;
                    }
                    byte [] crc32Bytes = crc32String.getBytes();
                    
                    boolean crcError = false;
                    for (int i=0; i<ReplicatorSinkProcessor.CRC32_BYTES; i++) {
                        if (crc32Bytes[i] != data[packetSize + i]) {
                            crcError = true;
                            break;
                        }
                    }
                    if (!crcError) {
                        node.preProcessReceivedData(data, address, "tcp");
                    }
                }
                  catch (IllegalStateException e) {
                    log.error("Queue is full for neighbor IP {}",inetSocketAddress.getAddress().getHostAddress());
                } catch (final RuntimeException e) {
                    log.error("Transaction processing runtime exception ",e);
                    neighbor.incInvalidTransactions();
                } catch (Exception e) {
                    log.info("Transaction processing exception " + e.getMessage());
                    log.error("Transaction processing exception ",e);
                }
            }
        } catch (IOException e) {
            log.error("***** NETWORK ALERT ***** TCP connection reset by neighbor {}, source closed, {}", neighbor.getHostAddress(), e.getMessage());
            replicatorSinkPool.shutdownSink(neighbor);
        } finally {
            if (neighbor != null) {
                if (finallyClose) {
                    replicatorSinkPool.shutdownSink(neighbor);
                    neighbor.setSource(null);
                    neighbor.setSink(null);
                    //if (!neighbor.isFlagged() ) {
                    //   Node.instance().getNeighbors().remove(neighbor);   
                    //}
                }                   
            }
        }
    }
}
