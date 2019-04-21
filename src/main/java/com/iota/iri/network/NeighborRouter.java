package com.iota.iri.network;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.conf.NodeConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.network.neighbor.NeighborState;
import com.iota.iri.network.neighbor.impl.NeighborImpl;
import com.iota.iri.network.pipeline.TransactionProcessingPipeline;
import com.iota.iri.network.protocol.Handshake;
import com.iota.iri.network.protocol.Protocol;
import com.iota.iri.utils.thread.ThreadIdentifier;
import com.iota.iri.utils.thread.ThreadUtils;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import net.openhft.hashing.LongHashFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A NeighborRouter takes care of managing connections to {@link Neighbor} instances, executing reads and writes from/to
 * neighbors and ensuring that wanted neighbors are connected. <br/>
 * A neighbor is identified by its identity which is made up of the IP address and the neighbor's own server socket port
 * for new incoming connections; for example: 153.59.34.101:15600. <br/>
 * The NeighborRouter and foreign neighbor will first exchange their server socket port via a handshaking packet, in
 * order to fully build up the identity between each other. If handshaking fails, the connection is dropped.
 */
public class NeighborRouter {

    private static final Logger log = LoggerFactory.getLogger(NeighborRouter.class);
    private static final String protocolPrefix = "tcp://";

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private static final SecureRandom rnd = new SecureRandom();
    private final ThreadIdentifier neighborRouterThreadIdentifier = new ThreadIdentifier("Neighbor Router");

    // external
    private NodeConfig config;
    private TransactionRequester txRequester;
    private TransactionProcessingPipeline txPipeline;

    // internal
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;

    // a mapping of host address + port (identity) to fully handshaked/connected neighbor
    private ConcurrentHashMap<String, Neighbor> connectedNeighbors = new ConcurrentHashMap<>();

    // neighbors which we want to connect to. entries are added upon initialization
    // of the NeighborRouter, when a neighbor is added through addNeighbors and
    // when a connection attempt failed.
    private Set<URI> reconnectPool = new CopyOnWriteArraySet<>();

    // contains the IP addresses of neighbors which are allowed to connect to us.
    // we use two sets as we allow multiple connections from a single IP address.
    private Set<String> hostsWhitelist = new HashSet<>();

    // contains the mapping of IP addresses to their domain names.
    // this is used to map an initialized connection to the domain which was defined
    // in the configuration or added on addNeighbors, to ensure, that a reconnect attempt
    // to the given neighbor is done through the resolved IP address of the origin domain.
    private Map<String, String> ipToDomainMapping = new HashMap<>();

    // contains the IP address + port as declared in the configuration file
    // plus subsequent entries added by addNeighbors.
    // the identity of a neighbor is its IP address and its own server socket port.
    private Set<String> allowedNeighbors = new HashSet<>();

    // used to silently drop connections. contains plain IP addresses
    private Set<String> hostsBlacklist = new CopyOnWriteArraySet<>();

    // used to force the selection loop to reconnect to wanted neighbors
    private AtomicBoolean forceReconnectAttempt = new AtomicBoolean(false);

    /**
     * Defines whether a neighbor got added/removed or not and the corresponding reason.
     */
    public enum NeighborMutOp {
        OK, SLOTS_FILLED, URI_INVALID, UNRESOLVED_DOMAIN, UNKNOWN_NEIGHBOR
    }

    /**
     * Initializes the dependencies of the {@link NeighborRouter}.
     * 
     * @param config      Network related configuration parameters
     * @param txRequester {@link TransactionRequester} instance to load hashes of requested transactions when gossiping
     * @param txPipeline  {@link TransactionProcessingPipeline} passed to newly created {@link Neighbor} instances
     */
    public void init(NodeConfig config, TransactionRequester txRequester, TransactionProcessingPipeline txPipeline) {
        this.config = config;
        this.txRequester = txRequester;
        this.txPipeline = txPipeline;
    }

    private void initNeighbors() {
        // parse URIs
        config.getNeighbors().stream().distinct().map(NeighborRouter::parseURI).filter(Optional::isPresent)
                .map(Optional::get).forEach(uri -> reconnectPool.add(uri));
    }

    /**
     * Starts a dedicated thread for the {@link NeighborRouter} and then starts the routing mechanism.
     */
    public void start() {
        ThreadUtils.spawnThread(this::route, neighborRouterThreadIdentifier);
    }

    /**
     * Starts the routing mechanism which first initialises connections to neighbors from the configuration and then
     * continuously reads and writes messages from/to neighbors. <br/>
     * This method will also try to reconnect to wanted neighbors by the given
     * {@link BaseIotaConfig#getReconnectAttemptIntervalSeconds()} value.
     */
    public void route() {
        log.info("starting neighbor router");

        // run selector loop
        try {
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            InetSocketAddress tcpBindAddr = new InetSocketAddress(config.getNeighboringSocketAddress(),
                    config.getNeighboringSocketPort());
            serverSocketChannel.socket().bind(tcpBindAddr);
            log.info("bound server TCP socket to {}", tcpBindAddr);

            // parse neighbors from configuration
            initNeighbors();

            // init connections to the wanted neighbors,
            // this also ensures that the whitelists are updated with the corresponding
            // IP addresses and domain name mappings.
            connectToWantedNeighbors();

            long lastReconnectAttempts = System.currentTimeMillis();
            long reconnectAttemptTimeout = TimeUnit.SECONDS.toMillis(config.getReconnectAttemptIntervalSeconds());

            while (!shutdown.get()) {
                int selected = selector.select(reconnectAttemptTimeout);
                if (shutdown.get()) {
                    break;
                }

                // reinitialize connections to wanted neighbors
                long now = System.currentTimeMillis();
                if (forceReconnectAttempt.get() || now - lastReconnectAttempts > reconnectAttemptTimeout) {
                    lastReconnectAttempts = now;
                    forceReconnectAttempt.set(false);
                    connectToWantedNeighbors();
                }

                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    try {
                        SelectionKey key = iterator.next();
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
                            Neighbor newNeighbor = new NeighborImpl<>(selector, newConn,
                                    remoteAddr.getAddress().getHostAddress(),
                                    Neighbor.UNKNOWN_REMOTE_SERVER_SOCKET_PORT, txPipeline);
                            String domain = ipToDomainMapping.get(remoteAddr.getAddress().getHostAddress());
                            if (domain != null) {
                                newNeighbor.setDomain(domain);
                            }
                            newNeighbor.send(Protocol.createHandshakePacket((char) config.getNeighboringSocketPort()));
                            log.info("new connection from {}, performing handshake...", newNeighbor.getHostAddress());
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
                            removeFromReconnectPool(neighbor);
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
                            try {
                                // the neighbor was faster than us to setup the connection
                                if (connectedNeighbors.containsKey(identity)) {
                                    log.info("neighbor {} is already connected", identity);
                                    removeFromReconnectPool(neighbor);
                                    key.cancel();
                                    continue;
                                }
                                if (channel.finishConnect()) {
                                    log.info("established connection to neighbor {}, now performing handshake...",
                                            identity);
                                    removeFromReconnectPool(neighbor);
                                    // remove connect interest
                                    key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                                    // add handshaking packet as the initial packet to send
                                    neighbor.send(
                                            Protocol.createHandshakePacket((char) config.getNeighboringSocketPort()));
                                    continue;
                                }
                            } catch (ConnectException ex) {
                                log.info(
                                        "couldn't establish connection to neighbor {}, will attempt to reconnect later",
                                        identity);
                                closeNeighborConnection(channel, identity, selector);
                            }
                            continue;
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
                                            log.info("closing connection to {} as handshake packet couldn't be written",
                                                    identity);
                                            closeNeighborConnection(channel, null, selector);
                                        } else {
                                            closeNeighborConnection(channel, identity, selector);
                                        }
                                        continue;
                                }
                            } catch (IOException ex) {
                                log.error("unable to send message to neighbor {}", identity);
                                closeNeighborConnection(channel, identity, selector);
                                addToReconnectPool(neighbor);
                                continue;
                            }
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
                                                SelectionKey srvKey = serverSocketChannel.keyFor(selector);
                                                srvKey.interestOps(0);
                                            }
                                        }
                                }
                            } catch (IOException ex) {
                                log.error("unable to read message from neighbor {}", identity);
                                closeNeighborConnection(channel, identity, selector);
                                addToReconnectPool(neighbor);
                            }
                        }
                    } finally {
                        iterator.remove();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (selector != null) {
                    // close all connections
                    for (SelectionKey keys : selector.keys()) {
                        keys.channel().close();
                    }
                    selector.close();
                }
                if (serverSocketChannel != null) {
                    serverSocketChannel.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            log.info("neighbor router stopped");
        }
    }

    private static LongHashFunction txCacheDigestHashFunc = LongHashFunction.xx();

    /**
     * Computes the digest of the given transaction data.
     * 
     * @param txBytes The raw byte encoded transaction data
     * @return The the digest of the transaction data
     */
    public static long getTxCacheDigest(byte[] txBytes) {
        return txCacheDigestHashFunc.hashBytes(txBytes);
    }

    /**
     * Adds the given neighbor to the 'reconnect pool' of neighbors which this node will try to reconnect to. If the
     * domain of the neighbor was known when the connection was established, it will be used to re-establish the
     * connection to the neighbor, otherwise the neighbor's current known IP address is used.<br/>
     * The neighbor is only added to the 'reconnect pool' if the neighbor was ready to send/process messages.
     * 
     * @param neighbor the neighbor to attempt to reconnect to
     */
    private void addToReconnectPool(Neighbor neighbor) {
        if (neighbor.getState() != NeighborState.READY_FOR_MESSAGES) {
            return;
        }
        // try to pull out the origin domain which was used to establish
        // the connection with this neighbor
        String domain = ipToDomainMapping.get(neighbor.getHostAddress());
        URI reconnectURI = null;
        if (domain != null) {
            reconnectURI = URI
                    .create(String.format("%s%s:%d", protocolPrefix, domain, neighbor.getRemoteServerSocketPort()));
        } else {
            reconnectURI = URI.create(String.format("%s%s", protocolPrefix, neighbor.getHostAddressAndPort()));
        }
        log.info("adding {} to the reconnect pool", reconnectURI);
        reconnectPool.add(reconnectURI);
    }

    /**
     * Ensures that the neighbor is removed from the reconnect pool by using the neighbor's IP address and domain
     * identity.
     * 
     * @param neighbor the neighbor to remove from the reconnect pool
     */
    private void removeFromReconnectPool(Neighbor neighbor) {
        URI raw = URI.create(String.format("%s%s", protocolPrefix, neighbor.getHostAddressAndPort()));
        reconnectPool.remove(raw);
        String domain = neighbor.getDomain();
        if (domain != null) {
            URI withDomain = URI
                    .create(String.format("%s%s:%d", protocolPrefix, domain, neighbor.getRemoteServerSocketPort()));
            reconnectPool.remove(withDomain);
        }
    }

    /**
     * Finalizes the handshaking to a {@link Neighbor} by reading the handshaking packet. <br/>
     * A faulty handshaking will drop the neighbor connection. <br/>
     * The connection will be dropped when:
     * <ul>
     * <li>the handshaking is faulty, meaning that a non handshaking packet was sent</li>
     * <li>{@link BaseIotaConfig#getMaxNeighbors()} has been reached</li>
     * <li>a non matching server socket port was communicated in the handshaking packet</li>
     * <li>the neighbor is already connected (checked by the identity)</li>
     * <li>the identity is not known (missing in {@link NeighborRouter#allowedNeighbors})</li>
     * </ul>
     * 
     * @param identity The identity of the neighbor
     * @param neighbor The {@link Neighbor} to finalize the handshaking with
     * @param channel  The associated {@link SocketChannel} of the {@link Neighbor}
     * @return whether the handshaking was successful
     * @throws IOException
     */
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
        if (availableNeighborSlotsFilled()) {
            log.error("dropping handshaked connection to neighbor {} as all neighbor slots are filled", identity);
            closeNeighborConnection(channel, null, selector);
            return false;
        }

        // after a successful handshake, the neighbor's server socket port is initialized
        // and thereby the identity of the neighbor is now fully distinguishable

        // check whether the remote server socket port from the origin URI
        // actually corresponds to the port advertised in the handshake packet
        int originPort = neighbor.getRemoteServerSocketPort();
        if (originPort != Neighbor.UNKNOWN_REMOTE_SERVER_SOCKET_PORT && originPort != handshake.getServerSocketPort()) {
            log.info(
                    "dropping handshaked connection from {} as neighbor advertised wrong server socket port (wanted {}, got {})",
                    identity, originPort, handshake.getServerSocketPort());
            closeNeighborConnection(channel, null, selector);
            return false;
        }
        neighbor.setRemoteServerSocketPort(handshake.getServerSocketPort());

        // check if neighbor is already connected
        String newIdentity = neighbor.getHostAddressAndPort();
        if (connectedNeighbors.containsKey(newIdentity)) {
            log.info("dropping handshaked connection from {} as neighbor is already connected", newIdentity);
            // pass just host address to not actually delete the already existing connection/neighbor
            closeNeighborConnection(channel, null, selector);
            return false;
        }

        // check if the given host + server socket port combination is actually defined in the config/wanted
        if (!config.isTestnet() && !allowedNeighbors.contains(newIdentity)) {
            log.info("dropping handshaked connection as neighbor from {} is not allowed to connect", newIdentity);
            closeNeighborConnection(channel, null, selector);
            return false;
        }

        log.info("neighbor connection to {} is ready for messages [latency {} ms]", newIdentity,
                System.currentTimeMillis() - handshake.getSentTimestamp());

        // the neighbor is now ready to process actual protocol messages
        neighbor.setState(NeighborState.READY_FOR_MESSAGES);

        // we finally add the neighbor to the connected neighbors map
        // if the handshake was successful and we got the remote port
        connectedNeighbors.put(neighbor.getHostAddressAndPort(), neighbor);

        // prevent reconnect attempts from the 'reconnect pool'
        // by constructing the source URI which was used for this neighbor
        removeFromReconnectPool(neighbor);

        return true;
    }

    /**
     * Initializes connections to wanted neighbors which are neighbors added by
     * {@link NeighborRouter#addNeighbor(String)} or defined in the configuration.<br/>
     *
     * A connection attempt is only made if the domain name of the neighbor could be resolved to its IP address.
     * Reconnect attempts will continuously try to resolve the domain name until the neighbor is explicitly removed via
     * {@link NeighborRouter#removeNeighbor(String)}.
     */
    private void connectToWantedNeighbors() {
        if (reconnectPool.isEmpty()) {
            return;
        }
        log.info("establishing connections to {} wanted neighbors {}", reconnectPool.size(), reconnectPool.toArray());
        reconnectPool.forEach(neighborURI -> {
            InetSocketAddress inetAddr = new InetSocketAddress(neighborURI.getHost(), neighborURI.getPort());
            try {
                // if in the meantime the neighbor connected to us, we don't need to reinitialize a connection.
                if (!inetAddr.isUnresolved()) {
                    String ipAddress = inetAddr.getAddress().getHostAddress();
                    String identity = String.format("%s:%d", ipAddress, inetAddr.getPort());
                    if (connectedNeighbors.containsKey(identity)) {
                        log.info("skipping connecting to {} as neighbor is already connected", identity);
                        reconnectPool.remove(neighborURI);
                        return;
                    }
                }

                initNeighborConnection(neighborURI, inetAddr);
            } catch (IOException e) {
                log.warn("unable to build socket for neighbor {}: {}", neighborURI, e.getMessage());
            }
        });
    }

    /**
     * Initializes a new {@link SocketChannel} to the given neighbor. <br/>
     * The IP address of the neighbor is removed from the blacklist, added to the whitelist and registered as an allowed
     * neighbor by its identity.
     * 
     * @param neighborURI The {@link URI} of the neighbor to connect to
     * @param addr        The {@link InetSocketAddress} extracted from the {@link URI}
     * @throws IOException if initializing the {@link SocketChannel} fails
     */
    private void initNeighborConnection(URI neighborURI, InetSocketAddress addr) throws IOException {
        if (addr.isUnresolved()) {
            log.warn("unable to resolve neighbor {} to IP address, will attempt to reconnect later", neighborURI);
            return;
        }

        String ipAddress = addr.getAddress().getHostAddress();

        // we are overriding a blacklist entry as we are explicitly trying to create a connection
        hostsBlacklist.remove(ipAddress);

        // allow connections from the given remote IP address to us.
        // this comes into place if our own initialized connection fails
        // but afterwards the added neighbor builds a connection to us.
        hostsWhitelist.add(ipAddress);

        // map the ip address to the domain
        ipToDomainMapping.put(ipAddress, addr.getHostString());

        // make the identity of the newly added neighbor clear, so that it gets rejected during handshaking
        // finalisation, in case the communicated server socket port is wrong.
        allowedNeighbors.add(String.format("%s:%d", addr.getAddress().getHostAddress(), addr.getPort()));

        // init new TCP socket channel
        SocketChannel tcpChannel = SocketChannel.open();
        tcpChannel.socket().setTcpNoDelay(true);
        tcpChannel.socket().setSoLinger(true, 0);
        tcpChannel.configureBlocking(false);
        tcpChannel.connect(addr);
        Neighbor neighbor = new NeighborImpl<>(selector, tcpChannel, addr.getAddress().getHostAddress(), addr.getPort(),
                txPipeline);
        neighbor.setDomain(addr.getHostName());
        tcpChannel.register(selector, SelectionKey.OP_CONNECT, neighbor);
    }

    /**
     * Checks whether the given host is allowed to connect given its IP address. <br/>
     * The connection is allowed when:
     * <ul>
     * <li>the IP address is not in the {@link NeighborRouter#hostsBlacklist}</li>
     * <li>{@link BaseIotaConfig#getMaxNeighbors()} has not been reached</li>
     * <li>is whitelisted in {@link NeighborRouter#hostsWhitelist} (if {@link BaseIotaConfig#isTestnet()} is false)</li>
     * </ul>
     * The IP address is blacklisted to mute it from subsequent connection attempts. The blacklisting is removed if the
     * IP address is added through {@link NeighborRouter#addNeighbor(String)}.
     * 
     * @param ipAddress       The IP address
     * @param newNeighborConn The {@link SocketChannel} to close if the connection is not allowed
     * @return true if allowed, false if not
     * @throws IOException if anything goes wrong closing the {@link SocketChannel}
     */
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
                log.info("blacklisting/dropping new connection as neighbor from {} is not defined in the config",
                        ipAddress);
                hostsBlacklist.add(ipAddress);
                newNeighborConn.close();
                return false;
            }
            log.info("new auto-tethering connection from {}", ipAddress);
        }
        return true;
    }

    /**
     * Closes the connection to the neighbor, re-registers the {@link ServerSocketChannel} for
     * {@link SelectionKey#OP_CONNECT} in case neighbor slots will be available again and finally removes the neighbor
     * from the connected neighbors map.
     * 
     * @param channel  {@link SocketChannel} to close
     * @param identity The identity of the neighbor, null must be passed if the neighbor should not be marked as not
     *                 connected.
     * @param selector The used {@link Selector}
     */
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
        if (identity == null) {
            return;
        }
        if (connectedNeighbors.remove(identity) != null) {
            log.info("removed neighbor {} from connected neighbors", identity);
        }
    }

    private boolean availableNeighborSlotsFilled() {
        // while this check if not thread-safe, initiated connections will be dropped
        // when their handshaking was done but already all neighbor slots are filled
        return connectedNeighbors.size() >= config.getMaxNeighbors();
    }

    /**
     * Adds the given neighbor to the {@link NeighborRouter}. The {@link} Selector is woken up and an attempt to connect
     * to wanted neighbors is initiated.
     * 
     * @param rawURI The URI of the neighbor
     * @return whether the neighbor was added or not
     */
    public NeighborMutOp addNeighbor(String rawURI) {
        if (availableNeighborSlotsFilled()) {
            return NeighborMutOp.SLOTS_FILLED;
        }
        Optional<URI> optUri = parseURI(rawURI);
        if (!optUri.isPresent()) {
            return NeighborMutOp.URI_INVALID;
        }
        URI neighborURI = optUri.get();
        // add to wanted neighbors
        reconnectPool.add(neighborURI);
        // wake up the selector and let it build connections to wanted neighbors
        forceReconnectAttempt.set(true);
        selector.wakeup();
        return NeighborMutOp.OK;
    }

    /**
     * Removes the given neighbor from the {@link NeighborRouter} by marking it for "disconnect". The neighbor is
     * disconnected as soon as the next selector loop is executed.
     * 
     * @param uri The URI of the neighbor
     * @return whether the neighbor was removed or not
     */
    public NeighborMutOp removeNeighbor(String uri) {
        Optional<URI> optUri = parseURI(uri);
        if (!optUri.isPresent()) {
            return NeighborMutOp.URI_INVALID;
        }

        URI neighborURI = optUri.get();
        InetSocketAddress inetAddr = new InetSocketAddress(neighborURI.getHost(), neighborURI.getPort());
        if (inetAddr.isUnresolved()) {
            log.warn("unable to remove neighbor {} as IP address couldn't be resolved", uri);
            return NeighborMutOp.UNRESOLVED_DOMAIN;
        }

        // remove the neighbor from connection attempts
        reconnectPool.remove(optUri.get());
        URI rawURI = URI.create(String.format("%s%s:%d", protocolPrefix, inetAddr.getAddress().getHostAddress(), neighborURI.getPort())));
        reconnectPool.remove(rawURI);

        String identity = String.format("%s:%d", inetAddr.getAddress().getHostAddress(), inetAddr.getPort());
        Neighbor neighbor = connectedNeighbors.get(identity);
        if (neighbor == null) {
            return NeighborMutOp.UNKNOWN_NEIGHBOR;
        }

        // the neighbor will be disconnected inside the selector loop
        neighbor.setState(NeighborState.MARKED_FOR_DISCONNECT);
        return NeighborMutOp.OK;
    }

    /**
     * Parses the given string to an URI. The URI must use "tcp://" as the protocol.
     *
     * @param uri The URI string to parse
     * @return the parsed URI, if parsed correctly
     */
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

    /**
     * Checks whether the given URI is valid. The URI is valid if it is not null and it uses TCP as the protocol.
     * 
     * @param uri The URI to check
     * @return true if the URI is valid, false if not
     */
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

    /**
     * Returns the {@link TransactionProcessingPipeline}.
     * 
     * @return the {@link TransactionProcessingPipeline} used by the {@link NeighborRouter}
     */
    public TransactionProcessingPipeline getTransactionProcessingPipeline() {
        return txPipeline;
    }

    /**
     * Gets all neighbors the {@link NeighborRouter} currently sees as either connected or attempts to build connections
     * to.
     * 
     * @return The neighbors
     */
    public List<Neighbor> getNeighbors() {
        List<Neighbor> neighbors = new ArrayList<>(connectedNeighbors.values());
        reconnectPool.forEach(uri -> {
            neighbors.add(new NeighborImpl<>(null, null, uri.getHost(), uri.getPort(), null));
        });
        return neighbors;
    }

    /**
     * Gets the currently connected neighbors.
     * 
     * @return The connected neighbors.
     */
    public Map<String, Neighbor> getConnectedNeighbors() {
        return connectedNeighbors;
    }

    /**
     * Gossips the given transaction to the given neighbor.
     *
     * @param neighbor The {@link Neighbor} to gossip the transaction to
     * @param tvm      The transaction to gossip
     * @throws Exception thrown when loading a hash of transaction to request fails
     */
    public void gossipTransactionTo(Neighbor neighbor, TransactionViewModel tvm) throws Exception {
        gossipTransactionTo(neighbor, tvm, false);
    }

    /**
     * Gossips the given transaction to the given neighbor.
     *
     * @param neighbor     The {@link Neighbor} to gossip the transaction to
     * @param tvm          The transaction to gossip
     * @param useHashOfTVM Whether to use the hash of the given transaction as the requested transaction hash or not
     * @throws Exception thrown when loading a hash of transaction to request fails
     */
    public void gossipTransactionTo(Neighbor neighbor, TransactionViewModel tvm, boolean useHashOfTVM)
            throws Exception {
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

    /**
     * Shut downs the {@link NeighborRouter} and all currently open connections.
     */
    public void shutdown() {
        shutdown.set(true);
        ThreadUtils.stopThread(neighborRouterThreadIdentifier);
    }
}
