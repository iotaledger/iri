package com.iota.iri;

import com.iota.iri.service.Node;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;

public class Neighbor {

    private final InetSocketAddress address;
    
    public int numberOfAllTransactions, numberOfNewTransactions, numberOfInvalidTransactions;

    public Neighbor(final InetSocketAddress address) {
        this.address = address;
    }

    public void send(final DatagramPacket packet) {

        try {
            packet.setSocketAddress(address);
            Node.instance().send(packet);
        } catch (final Exception e) {
        	// ignore
        }
    }
    
    @Override
    public boolean equals(final Object obj) {
        return address.equals(((Neighbor)obj).address);
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }
    
    public InetSocketAddress getAddress() {
		return address;
	}
    
    public int getNumberOfAllTransactions() {
		return numberOfAllTransactions;
	}
    
    public int getNumberOfInvalidTransactions() {
		return numberOfInvalidTransactions;
	}
    
    public int getNumberOfNewTransactions() {
		return numberOfNewTransactions;
	}
}
