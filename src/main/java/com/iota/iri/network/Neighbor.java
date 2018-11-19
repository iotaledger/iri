package com.iota.iri.network;

import org.apache.commons.lang3.StringUtils;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Neighbor {

    private final InetSocketAddress address;
    
    private long numberOfAllTransactions;
    private long numberOfNewTransactions;
    private long numberOfInvalidTransactions;
    private long randomTransactionRequests;
    private long numberOfSentTransactions;
    private long numberOfStaleTransactions;

    private final boolean flagged;
    public boolean isFlagged() {
        return flagged;
    }

    private final static AtomicInteger numPeers = new AtomicInteger(0);
    public static int getNumPeers() {
        return numPeers.get();
    }
    public static void incNumPeers() {
        numPeers.incrementAndGet();
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

    protected boolean matches(SocketAddress address) {
        if (address instanceof InetSocketAddress) {
            // faster than fallback
            InetAddress adr = ((InetSocketAddress) address).getAddress();
            return adr != null && StringUtils.equals(adr.getHostAddress(), hostAddress);
        } else { // fallback
            return address != null && address.toString().contains(hostAddress);
        }
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

    void incStaleTransactions() {
        numberOfStaleTransactions++;
    }

    public void incSentTransactions() {
        numberOfSentTransactions++;
    }

    public long getNumberOfAllTransactions() {
		return numberOfAllTransactions;
	}
    
    public long getNumberOfInvalidTransactions() {
		return numberOfInvalidTransactions;
	}

    public long getNumberOfStaleTransactions() {
        return numberOfStaleTransactions;
    }

    public long getNumberOfNewTransactions() {
		return numberOfNewTransactions;
	}

	public long getNumberOfRandomTransactionRequests() {
        return randomTransactionRequests;
    }
	
	public long getNumberOfSentTransactions() {
	    return numberOfSentTransactions;
	}

}
