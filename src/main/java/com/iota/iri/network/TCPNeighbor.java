package com.iota.iri.network;

import com.iota.iri.conf.Configuration;
import com.iota.iri.utils.Quiet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

public final class TCPNeighbor extends Neighbor implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(TCPNeighbor.class);

    private final Object lock = new Object();
    private TCPSink sink;
    private TCPSource source;
    private final AtomicInteger port = new AtomicInteger();


    private volatile boolean stopped = false;


    TCPNeighbor(InetSocketAddress address, boolean isConfigured) {
        super(address, isConfigured);
        this.port.set(address.getPort());
        this.sink = new TCPSink(this);
    }

    @Override
    public void close() {
        stop();
    }

    @Override
    public String connectionType() {
        return "tcp";
    }

    @Override
    public int getPort() {
        return port.get();
    }

    public void setPort(int port) {
        this.port.set(port);
    }


    public void stop() {
        this.stopped = true;
        Quiet.close(sink);
        Quiet.close(source);
        log.info("STOPPED: id {} for {}", this.hashCode(), getHostAddress(), getPort());
    }

    public boolean isStopped() {
        return stopped;
    }


    public void setSource(TCPSource source) throws Exception {
        synchronized (lock) {
            if (stopped) {
                throw new Exception("Neighbor stopped: " + hashCode() + ", " + getHostAddress() + ":" + getPort());
            }
            if (this.source != null) {
                log.warn("Shutting down source to replace with new one for {}, {}:{}", hashCode(), getHostAddress(), getPort());
                this.source.close();
            }
            this.source = source;
        }
    }


    @Override
    public void send(byte[] data) {
        try {
            // we drop packets if connecting or shutdown
            if (!sink.isShutdown()) {
                sink.sendData(data);
            }
        } catch (Exception e) {
            log.info("cannot send: {} for {}, {}:{}", e.getMessage(), hashCode(), getHostAddress(), getPort());
        }
    }

    @Override
    public boolean addressMatches(String str) {
        if (str.contains(getHostAddress())) {
            int index = str.indexOf(Integer.toString(port.get()));
            if (index > 0 && str.charAt(index - 1) == ':') {
                return true;
            }
        }
        return false;
    }

    public TCPSink.Stats getSinkStats() {
        return sink != null ? sink.getStats() : null;
    }
}
