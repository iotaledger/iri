package com.iota.iri.network;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.conf.NetworkConfig;
import com.iota.iri.conf.ProtocolConfig;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.network.neighbor.NeighborState;
import com.iota.iri.network.neighbor.impl.NeighborImpl;
import com.iota.iri.network.pipeline.TransactionProcessingPipeline;
import com.iota.iri.network.pipeline.TransactionProcessingPipelineImpl;
import com.iota.iri.network.protocol.Handshake;
import com.iota.iri.network.protocol.Protocol;
import com.iota.iri.utils.Converter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the neighbor router interface.
 */
public class NeighborRouterImpl implements NeighborRouter {

    private static final Logger log = LoggerFactory.getLogger(NeighborRouterImpl.class);
    private static final String PROTOCOL_PREFIX = "tcp://";

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private static final SecureRandom rnd = new SecureRandom();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Neighbor Router"));

    // external
    private NetworkConfig networkConfig;
    private ProtocolConfig protocolConfig;
    private TransactionRequester txRequester;
    private TransactionProcessingPipeline txPipeline;

    // internal
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;

    /**
     * a mapping of host address + port (identity) to fully handshaked/connected neighbor
     */
    private ConcurrentHashMap<String, Neighbor> connectedNeighbors = new ConcurrentHashMap<>();

    /**
     * neighbors which we want to connect to. entries are added upon initialization of the NeighborRouter, when a
     * neighbor is added through addNeighbors and when a connection attempt failed.
     */
    private Set<URI> reconnectPool = new CopyOnWriteArraySet<>();

    /**
     * contains the IP addresses of neighbors which are allowed to connect to us. we use two sets as we allow multiple
     * connections from a single IP address.
     */
    private Set<String> hostsWhitelist = new HashSet<>();

    /**
     * contains the mapping of IP addresses to their domain names. this is used to map an initialized connection to the
     * domain which was defined in the configuration or added on addNeighbors, to ensure, that a reconnect attempt to
     * the given neighbor is done through the resolved IP address of the origin domain.
     */
    private Map<String, String> ipToDomainMapping = new HashMap<>();

    /**
     * contains the IP address + port as declared in the configuration file plus subsequent entries added by
     * addNeighbors. the identity of a neighbor is its IP address and its own server socket port.
     */
    private Set<String> allowedNeighbors = new HashSet<>();

    /**
     * used to silently drop connections. contains plain IP addresses
     */
    private Set<String> hostsBlacklist = new CopyOnWriteArraySet<>();

    /**
     * used to force the selection loop to reconnect to wanted neighbors
     */
    private AtomicBoolean forceReconnectAttempt = new AtomicBoolean(false);

    /**
     * used to match against neighbor's coordinator address to cancel the connection in case it doesn't match this
     * node's own configured coordinator address
     */
    private byte[] byteEncodedCooAddress;

    /**
     * Creates a {@link NeighborRouterImpl}.
     *
     * @param networkConfig  Network related configuration parameters
     * @param protocolConfig Protocol related configuration parameters
     * @param txRequester    {@link TransactionRequester} instance to load hashes of requested transactions when
     *                       gossiping
     * @param txPipeline     {@link TransactionProcessingPipelineImpl} passed to newly created {@link Neighbor}
     *                       instances
     */
    public NeighborRouterImpl(NetworkConfig networkConfig, ProtocolConfig protocolConfig, TransactionRequester txRequester,
                              TransactionProcessingPipeline txPipeline) {

        this.txRequester = txRequester;
        this.txPipeline = txPipeline;
        this.networkConfig = networkConfig;
        this.protocolConfig = protocolConfig;

        // reduce the coordinator address to its byte encoded representation
        byte[] tritsEncodedCooAddress = new byte[protocolConfig.getCoordinator().toString().length()
                * Converter.NUMBER_OF_TRITS_IN_A_TRYTE];
        Converter.trits(protocolConfig.getCoordinator().toString(), tritsEncodedCooAddress, 0);
        byteEncodedCooAddress = new byte[Handshake.BYTE_ENCODED_COO_ADDRESS_BYTES_LENGTH];
        Converter.bytes(tritsEncodedCooAddress, byteEncodedCooAddress);

    }

    private void initNeighbors() {
        // parse URIs
        networkConfig.getNeighbors().stream()
                .distinct()
                .map(NeighborRouterImpl::parseURI)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(uri -> reconnectPool.add(uri));
    }

    @Override
    public void start() {
        executor.execute(this::route);
    }

    @Override
    public void route() {
        log.info("starting neighbor router");

        // run selector loop
        try {
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            InetSocketAddress tcpBindAddr = new InetSocketAddress(networkConfig.getNeighboringSocketAddress(),
                    networkConfig.getNeighboringSocketPort());
            serverSocketChannel.socket().bind(tcpBindAddr);
            log.info("bound server TCP socket to {}", tcpBindAddr);

            // parse neighbors from configuration
            initNeighbors();

            // init connections to the wanted neighbors,
            // this also ensures that the whitelists are updated with the corresponding
            // IP addresses and domain name mappings.
            connectToWantedNeighbors();

            long lastReconnectAttempts = System.currentTimeMillis();
            long reconnectAttemptTimeout = TimeUnit.SECONDS
                    .toMillis(networkConfig.getReconnectAttemptIntervalSeconds());

            while (!shutdown.get()) {
                selector.select(reconnectAttemptTimeout);
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
                            handleNewConnection(key);
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
                            handleConnect(channel, key, identity, neighbor);
                            continue;
                        }

                        if (key.isWritable() && !handleWrite(channel, key, identity, neighbor)) {
                            continue;
                        }

                        if (key.isReadable()) {
                            handleRead(channel, identity, neighbor);
                        }

                    } finally {
                        iterator.remove();
                    }
                }
            }
        } catch (IOException e) {
            log.error("error occurred in the neighbor router", e);
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
                log.error("error occurred while trying to gracefully shutdown the neighbor router", e);
            }
            log.info("neighbor router stopped");
        }
    }

    /**
     * Handles a new incoming connection and if it passes some initial conditions (via
     * {@link NeighborRouterImpl#okToConnect(String, SocketChannel)}), will start the handshaking process by placing a
     * handshake packet into the connection's send queue.
     * 
     * @param key the selection key associated with the server socket channel
     * @return whether the new connection was accepted
     */
    private boolean handleNewConnection(SelectionKey key) {
        try {
            // new connection from neighbor
            ServerSocketChannel srvSocket = (ServerSocketChannel) key.channel();
            SocketChannel newConn = srvSocket.accept();
            if (newConn == null) {
                return false;
            }
            InetSocketAddress remoteAddr = (InetSocketAddress) newConn.getRemoteAddress();
            if (!okToConnect(remoteAddr.getAddress().getHostAddress(), newConn)) {
                return false;
            }
            configureSocket(newConn);
            Neighbor newNeighbor = new NeighborImpl<>(selector, newConn, remoteAddr.getAddress().getHostAddress(),
                    Neighbor.UNKNOWN_REMOTE_SERVER_SOCKET_PORT, txPipeline);
            String domain = ipToDomainMapping.get(remoteAddr.getAddress().getHostAddress());
            if (domain != null) {
                newNeighbor.setDomain(domain);
            } else {
                newNeighbor.setDomain(remoteAddr.getAddress().getHostAddress());
            }

            newNeighbor.send(Handshake.createHandshakePacket((char) networkConfig.getNeighboringSocketPort(),
                    byteEncodedCooAddress, (byte) protocolConfig.getMwm()));
            log.info("new connection from {}, performing handshake...", newNeighbor.getHostAddress());
            newConn.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, newNeighbor);
            return true;
        } catch (IOException ex) {
            log.info("couldn't accept connection. reason: {}", ex.getMessage());
        }
        return false;
    }

    /**
     * <p>
     * Finalizes the underlying connection sequence by calling the channel's finishConnect() method.
     * </p>
     * <p>
     * <strong> This method does not finalize the logical protocol level connection, rather, it kicks of that process by
     * placing a handshake packet into the neighbor's send queue if the connection was successfully established.
     * </strong>
     * </p>
     * <p>
     * Connections passed into this method are self-initialized and were not accepted by the server socket channel.
     * </p>
     * <p>
     * In case the connection sequence fails, the connection will be dropped.
     * </p>
     * 
     * @param channel  the associated channel for the given connection
     * @param key      the associated selection key associated with the given connection
     * @param identity the identity of the connection/neighbor
     * @param neighbor the neighbor associated with this connection
     * @return whether the connection sequence finished successfully
     */
    private boolean handleConnect(SocketChannel channel, SelectionKey key, String identity, Neighbor neighbor) {
        try {
            // the neighbor was faster than us to setup the connection
            if (connectedNeighbors.containsKey(identity)) {
                log.info("neighbor {} is already connected", identity);
                removeFromReconnectPool(neighbor);
                key.cancel();
                return false;
            }
            if (channel.finishConnect()) {
                log.info("established connection to neighbor {}, now performing handshake...", identity);
                // remove connect interest
                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                // add handshaking packet as the initial packet to send
                neighbor.send(Handshake.createHandshakePacket((char) networkConfig.getNeighboringSocketPort(),
                        byteEncodedCooAddress, (byte) protocolConfig.getMwm()));
                return true;
            }
        } catch (IOException ex) {
            log.info("couldn't establish connection to neighbor {}, will attempt to reconnect later. reason: {}",
                    identity, ex.getMessage());
            closeNeighborConnection(channel, identity, selector);
        }
        return false;
    }

    /**
     * <p>
     * Handles the write readiness by the given channel by writing a message into its send buffer.
     * </p>
     * 
     * <p>
     * If there was no message to send, then the channel is de-registered from write interests. If the channel would not
     * be de-registered from write interests, the channel's write readiness would constantly fire this method even if
     * there's nothing to send, causing high CPU usage. Re-registering the channel for write interest is implicitly done
     * via the caller who's interested to send a message through the neighbor's implementation.
     * </p>
     * <p>
     * In case the write fails with an IOException, the connection will be dropped.
     * </p>
     *
     * @param channel  the associated channel for the given connection
     * @param key      the associated selection key associated with the given connection
     * @param identity the identity of the connection/neighbor
     * @param neighbor the neighbor associated with this connection
     * @return whether the write operation was successful or not
     */
    private boolean handleWrite(SocketChannel channel, SelectionKey key, String identity, Neighbor neighbor) {
        try {
            switch (neighbor.write()) {
                case 0:
                    // nothing was written, because no message was available to be sent.
                    // lets unregister this channel from write interests until at least
                    // one message is back available for sending.
                    key.interestOps(SelectionKey.OP_READ);
                    break;
                case -1:
                    if (neighbor.getState() == NeighborState.HANDSHAKING) {
                        log.info("closing connection to {} as handshake packet couldn't be written", identity);
                        closeNeighborConnection(channel, null, selector);
                    } else {
                        closeNeighborConnection(channel, identity, selector);
                    }
                    return false;
                default:
                    // bytes were written to the channel
            }
            return true;
        } catch (IOException ex) {
            log.warn("unable to write message to neighbor {}. reason: {}", identity, ex.getMessage());
            closeNeighborConnection(channel, identity, selector);
            addToReconnectPool(neighbor);
        }
        return false;
    }

    /**
     * <p>
     * Handles the read readiness by the given channel by reading from the channel's receive buffer.
     * </p>
     * <p>
     * In case the read fails with an IOException, the connection will be dropped.
     * </p>
     *
     * @param channel  the associated channel for the given connection
     * @param identity the identity of the connection/neighbor
     * @param neighbor the neighbor associated with this connection
     * @return whether the read operation was successful or not
     */
    private boolean handleRead(SocketChannel channel, String identity, Neighbor neighbor) {
        try {
            switch (neighbor.getState()) {
                case READY_FOR_MESSAGES:
                    if (neighbor.read() == -1) {
                        closeNeighborConnection(channel, identity, selector);
                        return false;
                    }
                    break;
                case HANDSHAKING:
                    if (finalizeHandshake(identity, neighbor, channel) && availableNeighborSlotsFilled()) {
                        // if all known neighbors or max neighbors are connected we are
                        // no longer interested in any incoming connections
                        // (as long as no neighbor dropped the connection)
                        SelectionKey srvKey = serverSocketChannel.keyFor(selector);
                        srvKey.interestOps(0);
                    }
                default:
                    // do nothing
            }
            return true;
        } catch (IOException ex) {
            log.warn("unable to read message from neighbor {}. reason: {}", identity, ex.getMessage());
            closeNeighborConnection(channel, identity, selector);
            addToReconnectPool(neighbor);
        }
        return false;
    }

    /**
     * Adjusts the given socket's configuration.
     * 
     * @param socketChannel the socket to configure
     * @throws IOException throw during adjusting the socket's configuration
     */
    private void configureSocket(SocketChannel socketChannel) throws IOException {
        socketChannel.socket().setTcpNoDelay(true);
        socketChannel.socket().setSoLinger(true, 0);
        socketChannel.configureBlocking(false);
    }

    /**
     * Adds the given neighbor to the 'reconnect pool' of neighbors which this node will try to reconnect to. If the
     * domain of the neighbor was known when the connection was established, it will be used to re-establish the
     * connection to the neighbor, otherwise the neighbor's current known IP address is used.<br/>
     * The neighbor is only added to the 'reconnect pool' if the neighbor was ready to send/process messages.
     * 
     * @param neighbor the neighbor to attempt to reconnect to
     * @return whether the neighbor got added to the reconnect pool or not
     */
    private boolean addToReconnectPool(Neighbor neighbor) {
        if (neighbor.getState() != NeighborState.READY_FOR_MESSAGES) {
            return false;
        }
        // try to pull out the origin domain which was used to establish
        // the connection with this neighbor
        String domain = ipToDomainMapping.get(neighbor.getHostAddress());
        URI reconnectURI;
        if (domain != null) {
            reconnectURI = URI
                    .create(String.format("%s%s:%d", PROTOCOL_PREFIX, domain, neighbor.getRemoteServerSocketPort()));
        } else {
            reconnectURI = URI.create(String.format("%s%s", PROTOCOL_PREFIX, neighbor.getHostAddressAndPort()));
        }
        log.info("adding {} to the reconnect pool", reconnectURI);
        return reconnectPool.add(reconnectURI);
    }

    /**
     * Ensures that the neighbor is removed from the reconnect pool by using the neighbor's IP address and domain
     * identity.
     * 
     * @param neighbor the neighbor to remove from the reconnect pool
     * @return whether the neighbor was removed from the reconnect pool or not
     */
    private boolean removeFromReconnectPool(Neighbor neighbor) {
        URI raw = URI.create(String.format("%s%s", PROTOCOL_PREFIX, neighbor.getHostAddressAndPort()));
        boolean removedByURI = reconnectPool.remove(raw);
        String domain = neighbor.getDomain();
        if (domain != null) {
            URI withDomain = URI
                    .create(String.format("%s%s:%d", PROTOCOL_PREFIX, domain, neighbor.getRemoteServerSocketPort()));
            if (reconnectPool.remove(withDomain)) {
                return true;
            }
        }
        return removedByURI;
    }

    /**
     * Finalizes the handshaking to a {@link Neighbor} by reading the handshaking packet. <br/>
     * A faulty handshaking will drop the neighbor connection. <br/>
     * The connection will be dropped when:
     * <ul>
     * <li>the handshaking is faulty, meaning that a non handshaking packet was sent</li>
     * <li>{@link BaseIotaConfig#getMaxNeighbors()} has been reached</li>
     * <li>the neighbor has a different coordinator address set as we do</li>
     * <li>the neighbor uses a different minimum weight magnitude than we do</li>
     * <li>a non matching server socket port was communicated in the handshaking packet</li>
     * <li>the neighbor is already connected (checked by the identity)</li>
     * <li>the identity is not known (missing in {@link NeighborRouterImpl#allowedNeighbors})</li>
     * </ul>
     * 
     * @param identity The identity of the neighbor
     * @param neighbor The {@link Neighbor} to finalize the handshaking with
     * @param channel  The associated {@link SocketChannel} of the {@link Neighbor}
     * @return whether the handshaking was successful
     * @throws IOException thrown when reading the handshake packet fails
     */
    private boolean finalizeHandshake(String identity, Neighbor neighbor, SocketChannel channel) throws IOException {
        Handshake handshake = neighbor.handshake();
        switch (handshake.getState()) {
            case INIT:
                // not fully read handshake packet
                return false;
            case FAILED:
                // faulty handshaking
                log.warn("dropping connection to neighbor {} as handshaking was faulty", identity);
                closeNeighborConnection(channel, identity, selector);
                return false;
            default:
                // do nothing
        }

        // drop the connection if in the meantime the available neighbor slots were filled
        if (availableNeighborSlotsFilled()) {
            log.error("dropping handshaked connection to neighbor {} as all neighbor slots are filled", identity);
            closeNeighborConnection(channel, null, selector);
            return false;
        }

        // check whether same MWM is used
        if (handshake.getMWM() != protocolConfig.getMwm()) {
            log.error("dropping handshaked connection to neighbor {} as it uses a different MWM ({} instead of {})",
                    identity, handshake.getMWM(), protocolConfig.getMwm());
            closeNeighborConnection(channel, null, selector);
            return false;
        }

        // check whether the neighbor actually uses the same coordinator address
        if (!Arrays.equals(byteEncodedCooAddress, handshake.getByteEncodedCooAddress())) {
            log.error("dropping handshaked connection to neighbor {} as it uses a different coordinator address",
                    identity);
            closeNeighborConnection(channel, null, selector);
            return false;
        }

        // check whether we support the supported protocol versions by the neighbor
        int supportedVersion = handshake.getNeighborSupportedVersion(Protocol.SUPPORTED_PROTOCOL_VERSIONS);
        if (supportedVersion <= 0) {
            log.error(
                    "dropping handshaked connection to neighbor {} as its highest supported protocol version {} is not supported",
                    identity, Math.abs(supportedVersion));
            closeNeighborConnection(channel, null, selector);
            return false;
        }
        neighbor.setProtocolVersion(supportedVersion);

        // after a successful handshake, the neighbor's server socket port is initialized
        // and thereby the identity of the neighbor is now fully distinguishable

        // check whether the remote server socket port from the origin URI
        // actually corresponds to the port advertised in the handshake packet
        int originPort = neighbor.getRemoteServerSocketPort();
        int handshakePort = handshake.getServerSocketPort();
        if (originPort != Neighbor.UNKNOWN_REMOTE_SERVER_SOCKET_PORT && originPort != handshakePort) {
            log.warn("dropping handshaked connection from {} as neighbor advertised "
                    + "wrong server socket port (wanted {}, got {})", identity, originPort, handshakePort);
            closeNeighborConnection(channel, null, selector);
            return false;
        }
        neighbor.setRemoteServerSocketPort(handshakePort);

        // check if neighbor is already connected
        String newIdentity = neighbor.getHostAddressAndPort();
        if (connectedNeighbors.containsKey(newIdentity)) {
            log.info("dropping handshaked connection from {} as neighbor is already connected", newIdentity);
            // pass just host address to not actually delete the already existing connection/neighbor
            closeNeighborConnection(channel, null, selector);
            return false;
        }

        // check if the given host + server socket port combination is actually defined in the config/wanted
        if (!networkConfig.isAutoTetheringEnabled() && !allowedNeighbors.contains(newIdentity)) {
            log.info("dropping handshaked connection as neighbor from {} is not allowed to connect", newIdentity);
            closeNeighborConnection(channel, null, selector);
            return false;
        }

        log.info("neighbor connection to {} is ready for messages [latency {} ms, protocol version {}]", newIdentity,
                System.currentTimeMillis() - handshake.getSentTimestamp(), supportedVersion);

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
                log.warn("unable to build socket for neighbor {}. reason: {}", neighborURI, e.getMessage());
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
        configureSocket(tcpChannel);
        tcpChannel.connect(addr);
        Neighbor neighbor = new NeighborImpl<>(selector, tcpChannel, addr.getAddress().getHostAddress(), addr.getPort(),
                txPipeline);
        neighbor.setDomain(addr.getHostString());
        tcpChannel.register(selector, SelectionKey.OP_CONNECT, neighbor);
    }

    /**
     * Checks whether the given host is allowed to connect given its IP address. <br/>
     * The connection is allowed when:
     * <ul>
     * <li>the IP address is not in the {@link NeighborRouterImpl#hostsBlacklist}</li>
     * <li>{@link BaseIotaConfig#getMaxNeighbors()} has not been reached</li>
     * <li>is whitelisted in {@link NeighborRouterImpl#hostsWhitelist} (if {@link BaseIotaConfig#isAutoTetheringEnabled()}
     * is false)</li>
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
            if (!networkConfig.isAutoTetheringEnabled()) {
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
            log.error("error while closing connection: {}", e.getMessage());
        }
        if (identity == null) {
            return;
        }
        if (connectedNeighbors.remove(identity) != null) {
            log.info("removed neighbor {} from connected neighbors", identity);
            // re-register the server socket for incoming connections as we will have a new slot open
            if (availableNeighborSlotsFilled()) {
                serverSocketChannel.keyFor(selector).interestOps(SelectionKey.OP_ACCEPT);
            }
        }
    }

    private boolean availableNeighborSlotsFilled() {
        // while this check is not thread-safe, initiated connections will be dropped
        // when their handshaking was done but already all neighbor slots are filled
        return connectedNeighbors.size() >= networkConfig.getMaxNeighbors();
    }

    @Override
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

    @Override
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
        boolean isSeen = reconnectPool.remove(neighborURI);
        URI rawURI = URI.create(String.format("%s%s:%d", PROTOCOL_PREFIX, inetAddr.getAddress().getHostAddress(),
                neighborURI.getPort()));
        reconnectPool.remove(rawURI);

        String identity = String.format("%s:%d", inetAddr.getAddress().getHostAddress(), inetAddr.getPort());
        Neighbor neighbor = connectedNeighbors.get(identity);

        if (neighbor == null) {
            if (isSeen) {
                return NeighborMutOp.OK;
            }
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
    private static Optional<URI> parseURI(final String uri) {
        if (uri.isEmpty()) {
            return Optional.empty();
        }

        URI neighborURI;
        try {
            neighborURI = new URI(uri);
        } catch (URISyntaxException e) {
            log.error("URI {} raised URI Syntax Exception. reason: {}", uri, e.getMessage());
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
    private static boolean isURIValid(final URI uri) {
        if (!uri.getScheme().equals("tcp")) {
            log.error("'{}' is not a valid URI schema, only TCP ({}) is supported", uri, PROTOCOL_PREFIX);
            return false;
        }
        return true;
    }

    @Override
    public TransactionProcessingPipeline getTransactionProcessingPipeline() {
        return txPipeline;
    }

    @Override
    public List<Neighbor> getNeighbors() {
        List<Neighbor> neighbors = new ArrayList<>(connectedNeighbors.values());
        reconnectPool.forEach(uri -> {
            // try to resolve the address of the neighbor which is not connected
            InetSocketAddress inetAddr = new InetSocketAddress(uri.getHost(), uri.getPort());
            String hostAddress = "";
            if(!inetAddr.isUnresolved()){
                hostAddress = inetAddr.getAddress().getHostAddress();
            }
            Neighbor neighbor = new NeighborImpl<>(null, null, hostAddress, uri.getPort(), null);
            // enforce the domain to be set, if the uri contains the IP address, the host address will not be empty
            // hence using the getNeighbors() HTTP API call will return a meaningful answer.
            neighbor.setDomain(uri.getHost());
            neighbors.add(neighbor);
        });
        return neighbors;
    }

    @Override
    public Map<String, Neighbor> getConnectedNeighbors() {
        return Collections.unmodifiableMap(connectedNeighbors);
    }

    @Override
    public void gossipTransactionTo(Neighbor neighbor, TransactionViewModel tvm) throws Exception {
        gossipTransactionTo(neighbor, tvm, false);
    }

    @Override
    public void gossipTransactionTo(Neighbor neighbor, TransactionViewModel tvm, boolean useHashOfTVM)
            throws Exception {
        byte[] requestedHash = null;
        if (!useHashOfTVM) {
            Hash hash = txRequester.transactionToRequest();
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

    @Override
    public void shutdown() {
        shutdown.set(true);
        executor.shutdownNow();
    }
}
