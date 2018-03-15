package com.iota.iri.network;

import com.iota.iri.conf.Configuration;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Neighbor {

    private final int tcpReceiverPort;
    private final int udpRecveiverPort;
    private final InetSocketAddress address;
    private final String hostAddress;
    private final boolean configured;

    private AtomicLong numberOfAllTransactions = new AtomicLong();
    private AtomicLong numberOfNewTransactions = new AtomicLong();
    private AtomicLong numberOfInvalidTransactions = new AtomicLong();
    private AtomicLong randomTransactionRequests = new AtomicLong();
    private AtomicLong numberOfSentTransactions = new AtomicLong();

    Neighbor(final int tcpReceiverPort, final int udpRecveiverPort, final InetSocketAddress address, boolean isConfigured) {
        this.tcpReceiverPort = tcpReceiverPort;
        this.udpRecveiverPort = udpRecveiverPort;
        this.address = address;
        this.hostAddress = address.getAddress().getHostAddress();
        this.configured = isConfigured;
    }

    ///////////////////////////////////////
    ///////////////////////////////////////

    public abstract int getPort();

    public abstract void send(byte[] data);

    public abstract String connectionType();

    public abstract boolean addressMatches(String socketAddress);

    ///////////////////////////////////////
    ///////////////////////////////////////


    public final int getTcpReceiverPort() {
        return tcpReceiverPort;
    }

    public final int getUdpRecveiverPort() {
        return udpRecveiverPort;
    }

    public boolean isConfigured() {
        return configured;
    }


    public final String getHostAddress() {
        return hostAddress;
    }

    public final InetSocketAddress getAddress() {
        return address;
    }

    /**
     * @param obj another Neighbor - preferably
     * @return true if a Neighbor and both the address and port match.
     */
    @Override
    public boolean equals(final Object obj) {
        return this == obj || obj instanceof Neighbor && address.equals(((Neighbor) obj).address);
    }


    @Override
    public int hashCode() {
        return address.hashCode();
    }

    void incAllTransactions() {
        numberOfAllTransactions.incrementAndGet();
    }

    void incNewTransactions() {
        numberOfNewTransactions.incrementAndGet();
    }

    void incRandomTransactionRequests() {
        randomTransactionRequests.incrementAndGet();
    }

    public void incInvalidTransactions() {
        numberOfInvalidTransactions.incrementAndGet();
    }

    void incSentTransactions() {
        numberOfSentTransactions.incrementAndGet();
    }

    ///////////////////////

    public long getNumberOfAllTransactions() {
        return numberOfAllTransactions.get();
    }

    public long getNumberOfInvalidTransactions() {
        return numberOfInvalidTransactions.get();
    }

    public long getNumberOfNewTransactions() {
        return numberOfNewTransactions.get();
    }

    public long getNumberOfRandomTransactionRequests() {
        return randomTransactionRequests.get();
    }

    public long getNumberOfSentTransactions() {
        return numberOfSentTransactions.get();
    }

}
