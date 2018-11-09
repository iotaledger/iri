package com.iota.iri.network;

import java.net.DatagramPacket;
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
        if (v < 0) {
            numPeers.set(0);
        }
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
    public abstract boolean matches(SocketAddress address);

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
