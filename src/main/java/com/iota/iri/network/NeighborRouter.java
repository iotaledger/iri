package com.iota.iri.network;

import com.iota.iri.conf.NodeConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.network.neighbor.NeighborState;
import com.iota.iri.network.protocol.Handshake;
import com.iota.iri.network.protocol.Protocol;
import com.iota.iri.network.neighbor.impl.NeighborImpl;
import com.iota.iri.network.pipeline.TxPipeline;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.thread.ThreadIdentifier;
import com.iota.iri.utils.thread.ThreadUtils;
import net.openhft.hashing.LongHashFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class NeighborRouter {

    private static final Logger log = LoggerFactory.getLogger(NeighborRouter.class);

    public final static AtomicBoolean SHUTDOWN = new AtomicBoolean(false);

    private static final SecureRandom rnd = new SecureRandom();
    private final ThreadIdentifier neighborRouterThreadIdentifier = new ThreadIdentifier("Neighbor Router");

    // external
    private NodeConfig config;
    private TransactionRequester txRequester;
    private TipRequester tipRequester;
    private Thread tipRequesterThread;
    private TxPipeline txPipeline;

    // internal
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;

    public void init(NodeConfig config, Tangle tangle, TransactionRequester txRequester,
                     LatestMilestoneTracker latestMilestoneTracker, TxPipeline txPipeline) {
        this.config = config;
        this.txRequester = txRequester;
        this.tipRequester = new TipRequester(this, tangle, latestMilestoneTracker, txRequester);
        this.txPipeline = txPipeline;
    }

    // a mapping of host address + port (identity) to fully handshaked/connected neighbor
    private ConcurrentHashMap<String, Neighbor> connectedNeighbors = new ConcurrentHashMap<>();

    // neighbors which we want to connect to. entries are added upon initialization
    // of the NeighborRouter, when a neighbor is added through addNeighbors and
    // when a connection attempt failed.
    private Set<URI> neighborsToConnectTo = new CopyOnWriteArraySet<>();

    // contains the IP addresses of neighbors which are allowed to connect to us.
    // we use two sets as we allow multiple connections from a single IP address.
    private Set<String> hostsWhitelist = new HashSet<>();

    // contains the IP address + port as declared in the configuration file
    // plus subsequent entries added by addNeighbors.
    // the identity of a neighbor is its IP address and its own server socket port.
    private Set<String> allowedNeighbors = new HashSet<>();

    // used to silently drop connections. contains plain IP addresses
    private Set<String> hostsBlacklist = new CopyOnWriteArraySet<>();

    private void initNeighbors() {
        // parse URIs
        config.getNeighbors().stream().distinct()
                .map(NeighborRouter::parseURI)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(uri -> neighborsToConnectTo.add(uri));
    }

    public void start() {
        ThreadUtils.spawnThread(this::route, neighborRouterThreadIdentifier);
    }

    public void route() {

        // start tip requester
        tipRequesterThread = new Thread(tipRequester);
        tipRequesterThread.start();

        // run selector loop
        try {
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            InetSocketAddress tcpBindAddr = new InetSocketAddress(config.getNeighboringSocketAddress(), config.getNeighboringSocketPort());
            serverSocketChannel.socket().bind(tcpBindAddr);
            log.info("bound server TCP socket to {}", tcpBindAddr);

            // build up connections to connectedNeighbors
            initNeighbors();
            connectToWantedNeighbors();

            long lastReconnectAttempts = System.currentTimeMillis();
            long reconnectAttemptTimeout = TimeUnit.SECONDS.toMillis(config.getReconnectAttemptIntervalSeconds());

            while (!SHUTDOWN.get()) {
                int selected = selector.select(reconnectAttemptTimeout);
                if (SHUTDOWN.get()) {
                    break;
                }

                // reinitialize connections to wanted neighbors
                long now = System.currentTimeMillis();
                if (now - lastReconnectAttempts > reconnectAttemptTimeout) {
                    lastReconnectAttempts = now;
                    connectToWantedNeighbors();
                }

                for (SelectionKey key : selector.selectedKeys()) {

                    if (key.isAcceptable()) {
                        // new connection from neighbor
                        ServerSocketChannel srvSocket = (ServerSocketChannel) key.channel();
                        SocketChannel newConn = srvSocket.accept();
                        if (newConn == null) {
                            continue;
                        }
                        InetSocketAddress remoteAddr = (InetSocketAddress) newConn.getRemoteAddress();
                        if (!okToConnect(remoteAddr.getAddress().getHostAddress(), newConn)) {
                            continue;
                        }
                        newConn.socket().setTcpNoDelay(true);
                        newConn.socket().setSoLinger(true, 0);
                        newConn.configureBlocking(false);
                        Neighbor newNeighbor = new NeighborImpl(selector, newConn, remoteAddr.getAddress().getHostAddress(), Neighbor.UNKNOWN_REMOTE_SERVER_SOCKET_PORT, txPipeline);
                        newNeighbor.send(Protocol.createHandshakePacket((char) config.getNeighboringSocketPort()));
                        log.info("initialized connection from neighbor {}", newNeighbor.getHostAddress());
                        newConn.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, newNeighbor);
                        continue;
                    }

                    SocketChannel channel = (SocketChannel) key.channel();
                    Neighbor neighbor = (Neighbor) key.attachment();
                    String identity = neighbor.getHostAddressAndPort();

                    // check whether marked for disconnect
                    if (neighbor.getState() == NeighborState.MARKED_FOR_DISCONNECT) {
                        allowedNeighbors.remove(identity);
                        closeNeighborConnection(channel, identity, selector);
                        continue;
                    }

                    if (key.isConnectable()) {
                        // in here we are handling a connection to a neighbor we have initialized after
                        // we booted up the server socket (from the config), we are attempting
                        // to reconnect to or was added via addNeighbor.
                        // there's an inherit race condition between us doing the initial connect to our
                        // defined neighbors and our neighbors connecting to us.
                        // thereby, there's a chance that a neighbor got already connected before
                        // our own initialized connection was fully connected, however, since
                        // only a fully handshaked neighbor is recognized as connected, the redundant
                        // connection will be closed after that connection's handshake.
                        log.info("finishing connection to neighbor {}", identity);
                        URI uri = URI.create(String.format("tcp://%s", identity));
                        try {
                            // the neighbor was faster than us to setup the connection
                            if (connectedNeighbors.containsKey(identity)) {
                                log.info("neighbor {} is already connected", identity);
                                key.cancel();
                                continue;
                            }
                            if (channel.finishConnect()) {
                                log.info("initialized connection to neighbor {}", identity);
                                neighborsToConnectTo.remove(uri);
                                // remove connect interest
                                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                                // add handshaking packet as the initial packet to send
                                neighbor.send(Protocol.createHandshakePacket((char) config.getNeighboringSocketPort()));
                                continue;
                            }
                        } catch (ConnectException ex) {
                            log.info("couldn't build connection to neighbor {}, will attempt to reconnect later", identity);
                            closeNeighborConnection(channel, identity, selector);
                            neighborsToConnectTo.add(uri);
                        }
                        continue;
                    }

                    if (key.isReadable()) {
                        try {
                            switch (neighbor.getState()) {
                                case READY_FOR_MESSAGES:
                                    if (neighbor.read() == -1) {
                                        closeNeighborConnection(channel, identity, selector);
                                    }
                                    break;
                                case HANDSHAKING:
                                    if (finalizeHandshake(identity, neighbor, channel)) {
                                        // if all known neighbors or max neighbors are connected we are
                                        // no longer interested in any incoming connections
                                        // (as long as no neighbor dropped the connection)
                                        if (availableNeighborSlotsFilled()) {
                                            key.interestOps(0);
                                        }
                                    }
                                    continue;
                            }
                        } catch (IOException ex) {
                            log.error("unable to read from socket of neighbor {}", identity);
                            closeNeighborConnection(channel, identity, selector);
                            if (neighbor.getState() == NeighborState.READY_FOR_MESSAGES) {
                                neighborsToConnectTo.add(URI.create(String.format("tcp://%s", identity)));
                            }
                            continue;
                        }
                    }

                    if (key.isWritable()) {
                        try {
                            switch (neighbor.write()) {
                                case 0:
                                    // nothing was written, probably because no message was available to send.
                                    // lets unregister this channel from write interests until at least
                                    // one message is back available for sending.
                                    key.interestOps(SelectionKey.OP_READ);
                                    break;
                                case -1:
                                    if (neighbor.getState() == NeighborState.HANDSHAKING) {
                                        log.info("closing neighbor connection to {} as handshake packet couldn't be written", identity);
                                    }
                                    closeNeighborConnection(channel, identity, selector);
                            }
                        } catch (IOException ex) {
                            log.error("unable to write to socket of neighbor {}", identity);
                            closeNeighborConnection(channel, identity, selector);
                            if (neighbor.getState() == NeighborState.READY_FOR_MESSAGES) {
                                neighborsToConnectTo.add(URI.create(String.format("tcp://%s", identity)));
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (selector != null) {
                    selector.close();
                }
                if (serverSocketChannel != null) {
                    serverSocketChannel.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            log.info("network protocol stopped");
        }
    }

    private static LongHashFunction txCacheDigestHashFunc = LongHashFunction.xx();

    public static long getTxCacheDigest(byte[] receivedData) {
        return txCacheDigestHashFunc.hashBytes(receivedData);
    }

    private boolean finalizeHandshake(String identity, Neighbor neighbor, SocketChannel channel) throws IOException {
        Handshake handshake = neighbor.handshake();
        switch (handshake.getState()) {
            case INIT:
                // not fully read handshake packet
                return false;
            case FAILED:
                // faulty handshaking
                log.error("dropping connection to neighbor {} as handshaking was faulty", identity);
                closeNeighborConnection(channel, identity, selector);
                return false;
        }

        // drop the connection if in the meantime the available neighbor slots were filled
        if(availableNeighborSlotsFilled()){
            log.error("dropping handshaked connection to neighbor {} as all neighbor slots are filled", identity);
            closeNeighborConnection(channel, identity, selector);
            return false;
        }

        // after a successful handshake, the neighbor's server socket port is initialized
        // and thereby the identity of the neighbor is now fully distinguishable

        // check whether the remote server socket port from the origin URI
        // actually corresponds to the port advertised in the handshake packet
        int originPort = neighbor.getRemoteServerSocketPort();
        if (originPort != Neighbor.UNKNOWN_REMOTE_SERVER_SOCKET_PORT &&
                originPort != handshake.getServerSocketPort()) {
            log.info("dropping handshaked connection from {} as neighbor advertised wrong server socket port (wanted {}, got {})", identity, originPort, handshake.getServerSocketPort());
            closeNeighborConnection(channel, identity, selector);
            return false;
        }
        neighbor.setRemoteServerSocketPort(handshake.getServerSocketPort());

        // check if neighbor is already connected
        String newIdentity = neighbor.getHostAddressAndPort();
        if (connectedNeighbors.containsKey(newIdentity)) {
            log.info("dropping handshaked connection from {} as neighbor is already connected", newIdentity);
            // pass just host address to not actually delete the already existing connection/neighbor
            closeNeighborConnection(channel, identity, selector);
            return false;
        }

        // check if the given host + server socket port combination is actually defined in the config/wanted
        if (!config.isTestnet() && !allowedNeighbors.contains(newIdentity)) {
            log.info("dropping handshaked connection as neighbor from {} is not allowed to connect", newIdentity);
            closeNeighborConnection(channel, identity, selector);
            return false;
        }

        log.info("neighbor connection to {} is handshaked and ready for messages [latency {} ms]", newIdentity, System.currentTimeMillis() - handshake.getSentTimestamp());

        // the neighbor is now ready to process actual protocol messages
        neighbor.setState(NeighborState.READY_FOR_MESSAGES);

        // we finally add the neighbor to the connected neighbors map
        // if the handshake was successful and we got the remote port
        connectedNeighbors.put(neighbor.getHostAddressAndPort(), neighbor);

        return true;
    }

    private void connectToWantedNeighbors() {
        if (neighborsToConnectTo.isEmpty()) {
            return;
        }
        log.info("attempting to build connection to {} wanted neighbors", neighborsToConnectTo.size());
        neighborsToConnectTo.forEach(neighborURI -> {
            InetSocketAddress inetAddr = new InetSocketAddress(neighborURI.getHost(), neighborURI.getPort());
            try {
                // if in the meantime the neighbor connected to us, we don't need to reinitialize a connection.
                if (!inetAddr.isUnresolved()) {
                    String ipAddress = inetAddr.getAddress().getHostAddress();
                    String identity = String.format("%s:%d", ipAddress, inetAddr.getPort());
                    if (connectedNeighbors.containsKey(identity)) {
                        log.info("skipping connecting to {} as neighbor is already connected", identity);
                        neighborsToConnectTo.remove(neighborURI);
                        return;
                    }
                }

                if (initNeighborConnection(neighborURI, inetAddr)) {
                    // the entry will be added again if the newly initialized connection fails
                    neighborsToConnectTo.remove(neighborURI);
                }
            } catch (IOException e) {
                log.warn("unable to build socket for neighbor {}: {}", neighborURI, e.getMessage());
            }
        });
    }

    private boolean initNeighborConnection(URI neighborURI, InetSocketAddress addr) throws IOException {
        if (addr.isUnresolved()) {
            log.warn("unable to resolve neighbor {} to IP address, will attempt to reconnect later", neighborURI);
            return false;
        }

        String ipAddress = addr.getAddress().getHostAddress();

        // we are overriding a blacklist entry as we are explicitly trying to create a connection
        hostsBlacklist.remove(ipAddress);

        // allow connections from the given remote IP address to us.
        // this comes into place if our own initialized connection fails
        // but afterwards the added neighbor builds a connection to us.
        hostsWhitelist.add(ipAddress);

        // make the identity of the newly added neighbor clear, so that it gets rejected during handshaking
        // finalisation, in case the communicated server socket port is wrong.
        allowedNeighbors.add(String.format("%s:%d", addr.getAddress().getHostAddress(), addr.getPort()));

        // init new TCP socket channel
        SocketChannel tcpChannel = SocketChannel.open();
        tcpChannel.socket().setTcpNoDelay(true);
        tcpChannel.socket().setSoLinger(true, 0);
        tcpChannel.configureBlocking(false);
        tcpChannel.connect(addr);
        Neighbor neighbor = new NeighborImpl(selector, tcpChannel, addr.getAddress().getHostAddress(), addr.getPort(), txPipeline);
        tcpChannel.register(selector, SelectionKey.OP_CONNECT, neighbor);
        return true;
    }

    private boolean okToConnect(String ipAddress, SocketChannel newNeighborConn) throws IOException {
        if (hostsBlacklist.contains(ipAddress)) {
            // silently drop connection
            newNeighborConn.close();
            return false;
        }
        if (availableNeighborSlotsFilled()) {
            log.info("dropping new connection from {} as all neighbor slots are filled", ipAddress);
            newNeighborConn.close();
            return false;
        }
        boolean whitelisted = hostsWhitelist.contains(ipAddress);
        if (!whitelisted) {
            if (!config.isTestnet()) {
                log.info("blacklisting/dropping new connection as neighbor from {} is not defined in the config", ipAddress);
                hostsBlacklist.add(ipAddress);
                newNeighborConn.close();
                return false;
            }
            log.info("new auto-tethering connection from {}", ipAddress);
        }
        return true;
    }

    private void closeNeighborConnection(SelectableChannel channel, String identity, Selector selector) {
        try {
            channel.close();
        } catch (IOException e) {
            return;
        }
        // re-register the server socket for incoming connections as we will have a new slot open
        if (availableNeighborSlotsFilled()) {
            SelectionKey key = serverSocketChannel.keyFor(selector);
            if (key != null) {
                key.interestOps(SelectionKey.OP_ACCEPT);
            }
        }
        if (connectedNeighbors.remove(identity) != null) {
            log.info("removing neighbor {} from connected neighbors", identity);
        } else {
            log.info("closed connection to neighbor {}", identity);
        }
    }

    private boolean availableNeighborSlotsFilled() {
        // while this check if not thread-safe, initiated connections will be dropped
        // when their handshaking was done but already all neighbor slots are filled
        return config.isTestnet() ? connectedNeighbors.size() >= config.getMaxNeighbors() : connectedNeighbors.size() >= config.getNeighbors().size();
    }

    public boolean addNeighbor(String rawURI) throws IOException {
        if (availableNeighborSlotsFilled()) {
            return false;
        }
        Optional<URI> optUri = parseURI(rawURI);
        if (!optUri.isPresent()) {
            return false;
        }
        URI neighborURI = optUri.get();
        InetSocketAddress inetAddr = new InetSocketAddress(neighborURI.getHost(), neighborURI.getPort());
        if (!initNeighborConnection(neighborURI, inetAddr)) {
            neighborsToConnectTo.add(neighborURI);
        }
        return true;
    }

    public boolean removeNeighbor(String rawURI) {
        Optional<URI> optUri = parseURI(rawURI);
        if (!optUri.isPresent()) {
            return false;
        }

        // remove the neighbor from connection attempts.
        // this is racy when a connection is initialized and key.isConnectable() is ongoing
        // as the neighbor will be added by it again
        neighborsToConnectTo.remove(optUri.get());

        URI neighborURI = optUri.get();
        InetSocketAddress inetAddr = new InetSocketAddress(neighborURI.getHost(), neighborURI.getPort());
        if (inetAddr.isUnresolved()) {
            log.warn("unable to remove neighbor {} as IP address couldn't be resolved", rawURI);
            return false;
        }
        String identity = String.format("%s:%d", inetAddr.getAddress().getHostAddress(), inetAddr.getPort());
        Neighbor neighbor = connectedNeighbors.get(identity);
        if (neighbor == null) {
            return false;
        }
        // the neighbor will be disconnected inside the selector loop
        neighbor.setState(NeighborState.MARKED_FOR_DISCONNECT);
        return true;
    }

    public static Optional<URI> parseURI(final String uri) {
        if (uri.isEmpty()) {
            return Optional.empty();
        }

        URI neighborURI;
        try {
            neighborURI = new URI(uri);
        } catch (URISyntaxException e) {
            log.error("URI {} raised URI Syntax Exception", uri);
            return Optional.empty();
        }
        if (!isURIValid(neighborURI)) {
            return Optional.empty();
        }
        return Optional.of(neighborURI);
    }

    public static boolean isURIValid(final URI uri) {
        if (uri == null) {
            log.error("Cannot read URI schema, please check neighbor config!");
            return false;
        }

        if (!uri.getScheme().equals("tcp")) {
            log.error("'{}' is not a valid URI schema", uri);
            return false;
        }
        return true;
    }

    public TxPipeline getTxPipeline() {
        return txPipeline;
    }

    public Map<String, Neighbor> getConnectedNeighbors() {
        return connectedNeighbors;
    }

    public void gossipTransactionTo(Neighbor neighbor, TransactionViewModel tvm) throws Exception {
        gossipTransactionTo(neighbor, tvm, false);
    }

    public void gossipTransactionTo(Neighbor neighbor, TransactionViewModel tvm, boolean useHashOfTVM) throws Exception {
        byte[] requestedHash = null;
        if (!useHashOfTVM) {
            Hash hash = txRequester.transactionToRequest(rnd.nextDouble() < config.getpSelectMilestoneChild());
            if (hash != null) {
                requestedHash = hash.bytes();
            }
        }

        if (requestedHash == null) {
            requestedHash = tvm.getHash().bytes();
        }

        ByteBuffer packet = Protocol.createTransactionGossipPacket(tvm, requestedHash);
        neighbor.send(packet);
        // tx might actually not be sent, we are merely putting it into the send queue
        // TODO: find a way to increment once we actually sent the txs into the channel
        neighbor.getMetrics().incrSentTransactionsCount();
    }

    public void shutdown() {
        SHUTDOWN.set(true);
    }
}
