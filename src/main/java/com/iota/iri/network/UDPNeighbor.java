package com.iota.iri.network;

import com.iota.iri.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Created by paul on 4/15/17.
 */
public final class UDPNeighbor extends Neighbor {
    private static final Logger log = LoggerFactory.getLogger(UDPNeighbor.class);

    private final DatagramSocket datagramSocket;

    private final String addressStr;
    private final String portStr;

    UDPNeighbor(InetSocketAddress address, DatagramSocket datagramSocket, boolean isConfigured) {
        super(address, isConfigured);
        this.datagramSocket = datagramSocket;

        // we make these because they don't change and they would otherwise get made for every call to 'matches'.
        this.addressStr = getAddress().toString();
        this.portStr = ":" + getPort();
    }

    @Override
    public String connectionType() {
        return "udp";
    }

    @Override
    public int getPort() {
        return getAddress().getPort();
    }

    @Override
    public void send(byte[] data) {
        DatagramPacket datagramPacket = new DatagramPacket(data, data.length);
        try {
            datagramPacket.setSocketAddress(getAddress());
            datagramSocket.send(datagramPacket);
            incSentTransactions();
        } catch (final Exception e) {
            log.error("Error sending UDP packet to [{}]: {}", getAddress(), e.toString());
        }
    }

    @Override
    public boolean addressMatches(String str) {
        if (addressStr.contains(str)) {
            if (str.contains(portStr)) {
                return true;
            }
        }
        return false;
    }
}
