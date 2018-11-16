package com.iota.iri.network;

import org.junit.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

public class UDPNeighborTest {

    private UDPNeighbor neighbor = new UDPNeighbor(address("localhost", 42), null, false);

    @Test
    public void sameIpWhenMatchesThenTrue() {
        assertThat(neighbor.matches(address("localhost", 42))).isTrue();
        assertThat(neighbor.matches(address("localhost", 666))).isTrue();
        assertThat(neighbor.matches(address("127.0.0.1", 42))).isTrue();
        assertThat(neighbor.matches(address("127.0.0.1", 666))).isTrue();
    }

    @Test
    public void differentIpWhenMatchesThenFalse() {
        assertThat(neighbor.matches(address("foo.bar.com", 42))).isFalse();
        assertThat(neighbor.matches(address("8.8.8.8", 42))).isFalse();
    }

    private InetSocketAddress address(String hostOrIp, int port) {
        return new InetSocketAddress(hostOrIp, port);
    }

}