package com.iota.iri.network;

import com.iota.iri.network.exec.StripedExecutor;
import com.iota.iri.utils.Quiet;
import com.iota.iri.utils.Shutdown;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;

public class TCPReplicator implements Shutdown {

    private static final Logger log = LoggerFactory.getLogger(TCPReplicator.class);

    private final StripedExecutor<Neighbor, byte[]> stripedExecutor;
    private final NeighborManager neighborManager;
    private final int maxPeers;
    private final boolean testnet;
    private final InetSocketAddress inetSocketAddress;
    private AsynchronousServerSocketChannel listener;

    private volatile boolean shutdown;

    public TCPReplicator(StripedExecutor<Neighbor, byte[]> stripedExecutor,
                         NeighborManager neighborManager,
                         int port,
                         int maxPeers,
                         boolean testnet) {
        this.stripedExecutor = stripedExecutor;
        this.neighborManager = neighborManager;
        this.inetSocketAddress = new InetSocketAddress(port);
        this.maxPeers = maxPeers;
        this.testnet = testnet;
    }

    public void init() throws Exception {
        start();
    }

    public void start() throws Exception {
        if (isShutdown()) {
            log.info("Cannot start ... already shutdown");
            return;
        }


        try {
            listener = AsynchronousServerSocketChannel.open()
                    .bind(inetSocketAddress);

            listener.accept(null,
                    TCPSource.newConnectionHandler(listener, neighborManager, stripedExecutor, maxPeers, testnet));

            log.info("Accepting TCP connections on port {} ...", inetSocketAddress.getPort());

        } catch (Exception e) {
            log.error("***** FATAL ***** Cannot create ServerSocketChannel at {}", inetSocketAddress, e.getMessage());
            shutdown();
            throw e;
        }
    }


    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    public void shutdown() {
        shutdown = true;
        log.info("Shutting down ... ");

        Quiet.close(listener);
        log.debug("AsynchronousServerSocketChannel closed. ");

        neighborManager.forEach(TCPNeighbor.class, Quiet::close);
        log.debug("All TCPNeighbors closed. ");
    }
}