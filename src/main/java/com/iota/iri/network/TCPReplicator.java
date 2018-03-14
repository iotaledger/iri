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

    private final StripedExecutor stripedExecutor;
    private final NeighborManager neighborManager;
    private final int maxPeers;
    private final boolean testnet;
    private int port;
    private AsynchronousServerSocketChannel listener;

    private volatile boolean shutdown;

    public TCPReplicator(StripedExecutor stripedExecutor,
                         NeighborManager neighborManager,
                         int port,
                         int maxPeers,
                         boolean testnet) {
        this.stripedExecutor = stripedExecutor;
        this.neighborManager = neighborManager;
        this.port = port;
        this.maxPeers = maxPeers;
        this.testnet = testnet;
    }

    public void init(int port) throws Exception {
        this.port = port;
        start();
    }

    public void start() throws Exception {
        if (isShutdown()) {
            log.info("Cannot create start ... already shutdown");
            return;
        }
        InetSocketAddress inetSocketAddress = new InetSocketAddress(port);
        try {
            listener = AsynchronousServerSocketChannel.open()
                    .bind(inetSocketAddress);

            listener.accept(null,
                    TCPSource.newConnectionHandler(listener, neighborManager, stripedExecutor, maxPeers, testnet));

            log.info("Accepting TCP connections on port {} ...", inetSocketAddress.getPort());

        } catch (Exception e) {
            log.error("***** FATAL ***** " +
                    "Cannot create ServerSocketChannel at {}", inetSocketAddress, e.getMessage());
            shutdown();
            throw e;
        }
    }


    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    public void shutdown() {
        log.info("Shutting down ... ");
        Quiet.close(listener);
        log.info("AsynchronousServerSocketChannel closed. ");
        neighborManager.forEach(TCPNeighbor.class, Quiet::close);
        log.info("All TCPNeighbors closed. ");
        shutdown = true;
    }
}