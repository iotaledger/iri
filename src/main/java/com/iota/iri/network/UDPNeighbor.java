package com.iota.iri.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

/**
 * Created by paul on 4/15/17.
 */
 
  /**
 * This class Extends {@link Neighbor} base class with UDP specific functionality. 
 * It keeps reference of socket and doesnt maintains any queue for UDP outgoing packets. 
 * 
 */
public class UDPNeighbor extends Neighbor {

    private static final Logger log = LoggerFactory.getLogger(UDPNeighbor.class);

    private final DatagramSocket socket;

    UDPNeighbor(final InetSocketAddress address, final DatagramSocket socket, final boolean isConfigured) {
        super(address, isConfigured);
        this.socket = socket;
    }

    /**
     * This is a blocking write and it is not necessary to copy the sent data.
     *
     * @param packet the packet to be sent immediately.
     */
    @Override
    public void send(DatagramPacket packet) {
        try {
            packet.setSocketAddress(getAddress());
            socket.send(packet);
            incSentTransactions();
        } catch (final Exception e) {
            log.error("Error sending UDP packet to [{}]: {}", getAddress(), e.toString());
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