package com.iota.iri;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.service.Node;

public abstract class Neighbor {
    
    private static final Logger log = LoggerFactory.getLogger(Neighbor.class);

    private final InetSocketAddress address;
    
    private int numberOfAllTransactions;
    private int numberOfNewTransactions;
    private int numberOfInvalidTransactions;
    

    private boolean flagged = false;
    
    public boolean isFlagged() {
        return flagged;
    }

    public void setFlagged(boolean flagged) {
        this.flagged = flagged;
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
    
    public void incAllTransactions() {
    	numberOfAllTransactions++;
    }
    
    public void incNewTransactions() {
    	numberOfNewTransactions++;
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
    
}
