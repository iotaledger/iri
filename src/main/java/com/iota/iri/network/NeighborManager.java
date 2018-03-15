package com.iota.iri.network;

import com.iota.iri.conf.Configuration;
import com.iota.iri.zmq.MessageQ;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class NeighborManager {
    private static final Logger log = LoggerFactory.getLogger(NeighborManager.class);


    private final Set<Neighbor> neighbors = new CopyOnWriteArraySet<Neighbor>();
    private final ConcurrentSkipListSet<String> rejectedAddresses = new ConcurrentSkipListSet<String>();

    private final Configuration configuration;
    private final MessageQ messageQ;
    private final int tcpReceiverPort;
    private final int udpReceiverPort;
    private DatagramSocket udpDatagramSocket;


    NeighborManager(Configuration configuration, MessageQ messageQ) {
        this.configuration = configuration;
        this.messageQ = messageQ;
        this.tcpReceiverPort = configuration.integer(Configuration.DefaultConfSettings.TCP_RECEIVER_PORT);
        this.udpReceiverPort = configuration.integer(Configuration.DefaultConfSettings.UDP_RECEIVER_PORT);
    }

    public void setUDPDatagramSocket(final DatagramSocket udpDatagramSocket) {
        this.udpDatagramSocket = udpDatagramSocket;
    }


    public boolean add(Neighbor neighbor) {
        return neighbors.add(neighbor);
    }

    public boolean removeNeighbor(final URI uri, boolean isConfigured) {
        final Neighbor neighbor = newNeighbor(uri, isConfigured);
        if (uri.getScheme().equals("tcp")) {
            forEach(TCPNeighbor.class, n -> {
                if (n.equals(neighbor)) {
                    n.stop();
                }
            });
        }
        return neighbors.remove(neighbor);
    }

    public void forEach(Consumer<Neighbor> consumer) {
        neighbors.forEach(consumer);
    }

    @SuppressWarnings("unchecked")
    public <T extends Neighbor> void forEach(Class<T> clazz, Consumer<T> consumer) {
        for (Neighbor n : neighbors) {
            if (clazz.isInstance(n)) {
                T t = (T) n;
                consumer.accept(t);
            }
        }
    }

    public static Optional<URI> uri(final String uri) {
        try {
            return Optional.of(new URI(uri));
        } catch (URISyntaxException e) {
            log.error("Uri {} raised exception", uri, Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
        }
        return Optional.empty();
    }


    private boolean isUriValid(final URI uri) {
        if (uri != null) {
            RuntimeException captured = null;
            if (uri.getScheme().equals("tcp") || uri.getScheme().equals("udp")) {
                try {
                    if ((new InetSocketAddress(uri.getHost(), uri.getPort()).getAddress() != null)) {
                        return true;
                    }
                } catch (RuntimeException e) {
                    captured = e;
                }
            }
            log.error("'{}' is not a valid uri schema or resolvable address.", uri);
            if (captured != null) throw captured;
            return false;
        }
        log.error("Cannot read uri schema, please check neighbor config!");
        return false;
    }


    public long count(Predicate<Neighbor> predicate) {
        return getNeighbors().stream().filter(predicate).count();
    }


    /**
     * This creates a neighbor but does not add it to the neighbours set.
     * Call add to add the neighbor to the registry.
     */
    public Neighbor newNeighbor(final URI uri, boolean isConfigured) {
        if (isUriValid(uri)) {
            if (uri.getScheme().equals("tcp")) {
                return new TCPNeighbor(tcpReceiverPort, udpReceiverPort, new InetSocketAddress(uri.getHost(), uri.getPort()), isConfigured);
            }
            if (uri.getScheme().equals("udp")) {
                return new UDPNeighbor(tcpReceiverPort, udpReceiverPort, new InetSocketAddress(uri.getHost(), uri.getPort()), udpDatagramSocket, isConfigured);
            }
        }
        throw new RuntimeException(uri.toString());
    }


    @SuppressWarnings("unchecked")
    public <T extends Neighbor> T findFirst(Class<T> clazz, Predicate<T> predicate) {
        for (Neighbor n : getNeighbors()) {
            if (clazz.isInstance(n)) {
                T t = (T) n;
                if (predicate.test(t)) {
                    return t;
                }
            }
        }
        return null;
    }

    public Neighbor findFirst(Predicate<Neighbor> predicate) {
        for (Neighbor neighbor : getNeighbors()) {
            if (predicate.test(neighbor)) {
                return neighbor;
            }
        }
        return null;
    }


    void parseNeighborsConfig() {
        Set<String> uniqueValues = new HashSet<>();
        for (String s : configuration.string(Configuration.DefaultConfSettings.NEIGHBORS).split(" ")) {
            if (uniqueValues.add(s)) {
                if (!s.isEmpty()) {
                    URI uri = uri(s).orElseThrow(() -> new IllegalArgumentException("Configured neighbor URI '" + s + "' could not be parsed"));
                    if (isUriValid(uri)) {
                        Neighbor u = newNeighbor(uri, true);
                        log.info("-> Adding neighbor from config: {} ", u.getAddress());
                        messageQ.publish("-> Adding Neighbor : %s", u.getAddress());
                        neighbors.add(u);
                    }
                }
            }
        }
    }

    public boolean addRejectedAddress(String address) {
        return rejectedAddresses.add(address);
    }

    public int size() {
        return getNeighbors().size();
    }

    public Set<Neighbor> getNeighbors() {
        return neighbors;
    }

    public TCPNeighbor newTCPNeighbor(InetSocketAddress inetSocketAddress, boolean b) {
        return new TCPNeighbor(tcpReceiverPort, udpReceiverPort, inetSocketAddress, b);
    }
}