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

public class Neighbor {
    
    private static final Logger log = LoggerFactory.getLogger(Neighbor.class);

    private final InetSocketAddress address;
    
    private int numberOfAllTransactions;
    private int numberOfNewTransactions;
    private int numberOfInvalidTransactions;
    
    private final ArrayBlockingQueue<ByteBuffer> sendQueue = new ArrayBlockingQueue<>(50);
    
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

    private final String hostAddress;
    
    public String getHostAddress() {
        return hostAddress;
    }
    
    private int tcpPort;
    
    
    public int getTcpPort() {
        return tcpPort;
    }

    public void setTcpPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    public void setSource(Socket source) {
        if (source == null) {
            if (this.source != null && !this.source.isClosed()) {
                try {
                    this.source.close();
                    log.info("Source {} closed", this.getHostAddress());
                } catch (IOException e) {
                    log.info("Source {} close failure {}", this.getHostAddress());
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
                    log.info("Sink {} closed", this.getHostAddress());
                } catch (IOException e) {
                    log.info("Source {} close failure {}", this.getHostAddress());
                }
            }
        }
        this.sink = sink;
    }

    public Neighbor(final InetSocketAddress address, boolean isTcp, boolean isConfigured) {
        this.address = address;
        this.hostAddress = address.getAddress().getHostAddress();
        this.tcpPort = address.getPort();
        this.tcpip = isTcp;
        this.flagged = isConfigured;
    }

    public void send(final DatagramPacket packet) {
        if (isTcpip()) {
            if (isTcpip()) {
                if ( sendQueue.remainingCapacity() == 0 ) {
                    sendQueue.poll();
                }
                sendQueue.add(ByteBuffer.wrap(packet.getData()));
            }
        }
        else {
            try {
                packet.setSocketAddress(address);
                Node.instance().send(packet);
            } catch (final Exception e) {
                log.error("UDP send error: {}",e.getMessage());
            }
        }
    }
    
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
    
    public ByteBuffer getNextMessage() throws InterruptedException {
        return (this.sendQueue.take());
    }
}
