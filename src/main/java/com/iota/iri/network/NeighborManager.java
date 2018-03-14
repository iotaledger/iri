package com.iota.iri.network;

import com.iota.iri.conf.Configuration;
import com.iota.iri.zmq.MessageQ;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
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
    private DatagramSocket udpDatagramSocket;


    NeighborManager(Configuration configuration, MessageQ messageQ) {
        this.configuration = configuration;
        this.messageQ = messageQ;
    }

    public void setUDPDatagramSocket(final DatagramSocket udpDatagramSocket) {
        this.udpDatagramSocket = udpDatagramSocket;
    }


    public <T extends Neighbor> T findFirstAddressMatch(InetSocketAddress address, Class<T> neighborClass) {
        String check = address.toString();
        return findFirst(neighborClass, n -> n.addressMatches(check));
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

    public <T extends Neighbor> void forEach(Class<T> clazz, Consumer<T> consumer) {
        neighbors.stream()
                .filter(clazz::isInstance)
                .map(n -> (T) n)
                .forEach(n -> consumer.accept((T) n));
    }

    private boolean isUriValid(final URI uri) {
        if (uri != null) {
            if (uri.getScheme().equals("tcp") || uri.getScheme().equals("udp")) {
                if ((new InetSocketAddress(uri.getHost(), uri.getPort()).getAddress() != null)) {
                    return true;
                }
            }
            log.error("'{}' is not a valid uri schema or resolvable address.", uri);
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
                return new TCPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), isConfigured);
            }
            if (uri.getScheme().equals("udp")) {
                return new UDPNeighbor(new InetSocketAddress(uri.getHost(), uri.getPort()), udpDatagramSocket, isConfigured);
            }
        }
        throw new RuntimeException(uri.toString());
    }


    public <T extends Neighbor> T findFirst(Class<T> clazz, Predicate<T> predicate) {
        return getNeighbors().stream()
                .filter(clazz::isInstance)
                .map(n -> (T) n)
                .filter(predicate)
                .findFirst().orElse(null);
    }

    public Neighbor findFirst(Predicate<Neighbor> predicate) {
        return getNeighbors().stream()
                .filter(predicate)
                .findFirst().orElse(null);
    }


    void parseNeighborsConfig() {
        Arrays.stream(configuration.string(Configuration.DefaultConfSettings.NEIGHBORS).split(" ")).distinct()
                .filter(s -> !s.isEmpty()).map(Node::uri).map(Optional::get)
                .filter(u -> isUriValid(u))
                .map(u -> newNeighbor(u, true))
                .peek(u -> {
                    log.info("-> Adding neighbor from config: {} ", u.getAddress());
                    messageQ.publish("-> Adding Neighbor : %s", u.getAddress());
                }).forEach(neighbors::add);
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
        return new TCPNeighbor(inetSocketAddress, b);
    }
}