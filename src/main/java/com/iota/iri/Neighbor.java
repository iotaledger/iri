package com.iota.iri;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.service.Node;
import com.iota.iri.service.storage.ReplicatorSourceProcessor;

public class Neighbor {
    
    private static final Logger log = LoggerFactory.getLogger(Neighbor.class);

    private final InetSocketAddress address;
    
    private int numberOfAllTransactions;
    private int numberOfNewTransactions;
    private int numberOfInvalidTransactions;
    
    public ArrayBlockingQueue<ByteBuffer> sendQueue = new ArrayBlockingQueue<>(50);
    
    private boolean flagged = false;
    
    public boolean isFlagged() {
        return flagged;
    }

    public void setFlagged(boolean flagged) {
        this.flagged = flagged;
    }
    
    private boolean tcpip = false;
    
    public boolean isTcpip() {
        return tcpip;
    }

    public void setTcpip(boolean tcpip) {
        this.tcpip = tcpip;
    }

    private Socket source = null;
    
    public Socket getSource() {
        return source;
    }

    public void setSource(Socket source) {
        if (source == null) {
            if (this.source != null && !this.source.isClosed()) {
                try {
                    this.source.close();
                    log.info("Source {} closed", getAddress().getAddress().getHostAddress());
                } catch (IOException e) {
                    log.info("Source {} close failure {}", getAddress().getAddress().getHostAddress(), e.getMessage());
                }
            }
        }
        this.source = source;
    }

    private Socket sink = null;

    public Socket getSink() {
        return sink;
    }

    public void setSink(Socket sink) {
        if (sink == null) {
            if (this.sink != null && !this.sink.isClosed()) {
                try {
                    this.sink.close();
                    log.info("Sink {} closed", getAddress().getAddress().getHostAddress());
                } catch (IOException e) {
                    log.info("Source {} close failure {}", getAddress().getAddress().getHostAddress(), e.getMessage());
                }
            }
        }
        this.sink = sink;
    }

    private boolean waitingForSinkOpen;
    
    public boolean isWaitingForSinkOpen() {
        return waitingForSinkOpen;
    }

    public void setWaitingForSinkOpen(boolean waitingForSinkOpen) {
        this.waitingForSinkOpen = waitingForSinkOpen;
    }

    public Neighbor(final InetSocketAddress address, boolean isTcp, boolean isConfigured) {
        this.address = address;
        this.tcpip = isTcp;
        this.flagged = isConfigured;
        this.waitingForSinkOpen = false;
    }

    public void send(final DatagramPacket packet) {
        if (isTcpip()) {
            if ( sendQueue.remainingCapacity() > 0 ) {
                sendQueue.add(ByteBuffer.wrap(packet.getData()));
            }
        }
        else {
            try {
                packet.setSocketAddress(address);
                Node.instance().send(packet);
            } catch (final Exception e) {
                // ignore
            }
        }
    }
    
    @Override
    public boolean equals(final Object obj) {
    	if (this == obj) {
            return true;
    	}
        if ((obj == null) || (obj.getClass() != this.getClass())) {
            return false;
        }
        return address.equals(((Neighbor)obj).address);
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
    
    public ByteBuffer getNextMessage() throws InterruptedException {
        return (this.sendQueue.take());
    }
}
