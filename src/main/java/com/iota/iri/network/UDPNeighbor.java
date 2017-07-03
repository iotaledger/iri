package com.iota.iri.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Created by paul on 4/15/17.
 */
public class UDPNeighbor extends Neighbor {
    private static final Logger log = LoggerFactory.getLogger(UDPNeighbor.class);

    private DatagramSocket socket;

    public UDPNeighbor(final InetSocketAddress address, final DatagramSocket socket, final boolean isConfigured) {
        super(address, isConfigured);
        this.socket = socket;
    }

    @Override
    public void send(DatagramPacket packet) {
        try {
            packet.setSocketAddress(getAddress());
            socket.send(packet);
            incSentTransactions();
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

    @Override
    public boolean matches(SocketAddress address) {
        if (this.getAddress().toString().contains(address.toString())) {
            int port = this.getAddress().getPort();
            if (address.toString().contains(Integer.toString(port))) {
                return true;
            }
        }
        return false;
    }

}
