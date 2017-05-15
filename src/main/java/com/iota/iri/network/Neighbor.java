package com.iota.iri.network;

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Neighbor {
    
    private static final Logger log = LoggerFactory.getLogger(Neighbor.class);

    private final InetSocketAddress address;
    
    private int numberOfAllTransactions;
    private int numberOfNewTransactions;
    private int numberOfInvalidTransactions;
    private int randomTransactionRequests;

    private boolean flagged = false;
    public boolean isFlagged() {
        return flagged;
    }
    public void setFlagged(boolean flagged) {
        this.flagged = flagged;
    }
    
    private final static AtomicInteger numPeers = new AtomicInteger(0);
    public static int getNumPeers() {
        return numPeers.get();
    }
    public static void incNumPeers() {
        numPeers.incrementAndGet();
    }
    public static void decNumPeers() {
        int v = numPeers.decrementAndGet();
        if (v < 0) numPeers.set(0);;
    }

    private final String hostAddress;

    public String getHostAddress() {
        return hostAddress;
    }

    public Neighbor(final InetSocketAddress address, boolean isConfigured) {
        this.address = address;
        this.hostAddress = address.getAddress().getHostAddress();
        this.flagged = isConfigured;
    }

    public abstract void send(final DatagramPacket packet);
    public abstract int getPort();
    public abstract String connectionType();

    @Override
    public boolean equals(final Object obj) {
        return this == obj || !((obj == null) || (obj.getClass() != this.getClass())) && address.equals(((Neighbor) obj).address);
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }
    
    public InetSocketAddress getAddress() {
		return address;
	}
    
    void incAllTransactions() {
    	numberOfAllTransactions++;
    }
    
    void incNewTransactions() {
    	numberOfNewTransactions++;
    }

    void incRandomTransactionRequests() {
        randomTransactionRequests++;
    }

    public void incInvalidTransactions() {
    	numberOfInvalidTransactions++;
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

	public int getNumberOfRandomTransactionRequests() {
        return randomTransactionRequests;
    }
    
}
