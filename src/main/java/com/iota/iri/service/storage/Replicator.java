package com.iota.iri.service.storage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.Neighbor;
import com.iota.iri.conf.Configuration;
import com.iota.iri.conf.Configuration.DefaultConfSettings;
import com.iota.iri.service.Node;

public class Replicator {
    
    private static final Logger log = LoggerFactory.getLogger(Replicator.class);
    
    private List<Neighbor> neighbors = new CopyOnWriteArrayList<>();
    
    public synchronized List<Neighbor> getNeighbors() {
        return neighbors;
    }
    
    public synchronized Neighbor getNeighborByAddress(InetSocketAddress sa) {
        for (final Neighbor neighbor : neighbors) {
            
            if (neighbor.getAddress().getAddress().getHostAddress().equals(sa.getAddress().getHostAddress())) {
                return neighbor;
            }
        }
        return null;
    }
    
    //if (neighbor.getAddress().equals(receivingPacket.getSocketAddress())) {

    public synchronized void addNeighbor(Neighbor neighbor) {
        this.neighbors.add(neighbor);
    }

    public void init() throws IOException {
        
        Arrays.stream(Configuration.string(DefaultConfSettings.NEIGHBORS).split(" ")).distinct()
                .filter(s -> !s.isEmpty()).map(Node::uri).map(Optional::get).peek(u -> {
                    if (!"tcp".equals(u.getScheme())) {
                        log.warn("WARNING: '{}' is not a valid tcp:// uri schema.", u);
                    }
                }).filter(u -> "tcp".equals(u.getScheme()))
                .map(u -> new Neighbor(new InetSocketAddress(u.getHost(), u.getPort()))).peek(u -> {
                    if (Configuration.booling(DefaultConfSettings.DEBUG)) {
                        log.debug("-> Adding neighbor : {} ", u.getAddress());
                    }
                }).forEach(neighbors::add);

        ReplicatorSinkPool.instance().init();
        new Thread(ReplicatorSourcePool.instance()).start();
        log.info("Started ReplicatorSourcePool");
    }
    
    public void shutdown() {
        
    }
    
    private static Replicator instance = new Replicator();

    private Replicator() {
    }

    public static Replicator instance() {
        return instance;
    }

}
