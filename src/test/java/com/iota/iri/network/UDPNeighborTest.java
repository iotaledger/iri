package com.iota.iri.network;

import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UDPNeighborTest {

    private final UDPNeighbor neighbor = new UDPNeighbor(address("localhost", 42), null, false);

    @Test
    public void sameIpWhenMatchesThenTrue() {
        assertTrue("expected match", neighbor.matches(address("localhost", 42)));
        assertTrue("expected match", neighbor.matches(address("localhost", 666)));
        assertTrue("expected match", neighbor.matches(address("127.0.0.1", 42)));
        assertTrue("expected match", neighbor.matches(address("127.0.0.1", 666)));
    }

    @Test
    public void differentIpWhenMatchesThenFalse() {
        assertFalse("expected no match", neighbor.matches(address("foo.bar.com", 42)));
        assertFalse("expected no match", neighbor.matches(address("8.8.8.8", 42)));
        assertFalse("expected no match", neighbor.matches(null));
        assertFalse("expected no match", neighbor.matches(new SocketAddress() {}));
    }

    private InetSocketAddress address(String hostOrIp, int port) {
        return new InetSocketAddress(hostOrIp, port);
    }

}