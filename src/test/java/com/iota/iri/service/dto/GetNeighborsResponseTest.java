package com.iota.iri.service.dto;

import com.iota.iri.network.Neighbor;
import com.iota.iri.network.TCPNeighbor;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class GetNeighborsResponseTest {

    private List<Neighbor> neighbors;

    @Before
    public void setUp() throws Exception {
        InetAddress inetAddress = InetAddress.getByAddress("test.test", new byte[]{10, 0, 0, 1});
        InetSocketAddress inetSocketAddress = new InetSocketAddress(inetAddress, 8888);
        Neighbor neighbor = new TCPNeighbor(inetSocketAddress, true);
        neighbors = Collections.singletonList(neighbor);
    }

    @Test
    public void getNeighbors() {
        GetNeighborsResponse response = (GetNeighborsResponse) GetNeighborsResponse.create(neighbors);
        assertEquals("One neighbor must exist", 1, response.getNeighbors().length);
        assertNotEquals("test.test", response.getNeighbors()[0].getAddress());
        assertEquals("Must return IP address and port", "10.0.0.1:8888", response.getNeighbors()[0].getAddress());
    }

    @Test
    public void create() {
        GetNeighborsResponse response = (GetNeighborsResponse) GetNeighborsResponse.create(neighbors);
        assertEquals("One neighbor must exist", 1, response.getNeighbors().length);
    }
}
