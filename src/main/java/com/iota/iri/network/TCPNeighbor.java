package com.iota.iri.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by paul on 4/15/17.
 */
 
 /**
 * This class Extends {@link Neighbor} base class with TCP specific functionality. 
 * It keeps reference of Source and Sink while maintaining a sendQueue for keeping
 * outgoing requests. 
 * 
 */
public class TCPNeighbor extends Neighbor {
    private static final Logger log = LoggerFactory.getLogger(Neighbor.class);
    private int tcpPort;

    private final ArrayBlockingQueue<ByteBuffer> sendQueue = new ArrayBlockingQueue<>(10);
    private boolean stopped = false;

    public TCPNeighbor(InetSocketAddress address, boolean isConfigured) {
        super(address, isConfigured);
        this.tcpPort = address.getPort();
    }

    private Socket source = null;

    public Socket getSource() {
        return source;
    }

    public void clear() {
        setSource(null);
        setSink(null);
        this.stopped = true;
    }

    public boolean isStopped() {
        return stopped;
    }

    public void setSource(Socket source) {
        if (source == null) {
            if (this.source != null && !this.source.isClosed()) {
                try {
                    this.source.close();
                    log.info("Source {} closed", this.getHostAddress());
                } catch (IOException e) {
                    log.error("Source {} close failure", this.getHostAddress(), e);
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
                    log.error("Sink {} close failure: {}", this.getHostAddress(), e.toString());
                }
            }
        }
        this.sink = sink;
    }

    /**
     * This is a non-blocking write and that makes it necessary to make a defensive copy of the sent data.
     *
     * @param packet the data to be queued for sending.
     */
    @Override
    public void send(DatagramPacket packet) {
        synchronized (sendQueue) {
            if (sendQueue.remainingCapacity() == 0) {
                sendQueue.poll();
                log.info("Sendqueue full...dropped 1 tx");
            }
            byte[] bytes = packet.getData().clone();
            sendQueue.add(ByteBuffer.wrap(bytes));
        }

    }

    @Override
    public int getPort() {
        return tcpPort;
    }

    @Override
    public String connectionType() {
        return "tcp";
    }

    public void setTcpPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    public ByteBuffer getNextMessage() throws InterruptedException {
        return (this.sendQueue.poll(10000, TimeUnit.MILLISECONDS));
    }

}
