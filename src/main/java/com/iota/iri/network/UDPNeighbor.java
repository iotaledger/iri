package com.iota.iri.network;

import com.iota.iri.Neighbor;
import com.iota.iri.service.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;

/**
 * Created by paul on 4/15/17.
 */
public class UDPNeighbor extends Neighbor {
    private static final Logger log = LoggerFactory.getLogger(Neighbor.class);

    public UDPNeighbor(InetSocketAddress address, boolean isConfigured) {
        super(address, isConfigured);
    }

    @Override
    public void send(DatagramPacket packet) {
        try {
            packet.setSocketAddress(getAddress());
            Node.instance().send(packet);
        } catch (final Exception e) {
            log.error("UDP send error: {}",e.getMessage());
        }
    }

    @Override
    public int getPort() {
        return getAddress().getPort();
    }

    @Override
    public String connectionType() {
        return "udp";
    }
}
