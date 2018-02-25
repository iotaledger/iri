package com.iota.iri.network;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class UDPNeighborTest {

    private static final String HOST = "localhost";
    private static final int PORT = 42;

    @Mock
    private DatagramSocket udpSocket;

    private final DatagramPacket packet = new DatagramPacket(new byte[]{}, 0, 0);

    @Test
    public void whenSendThenSendPacketToSocket() throws IOException {
        UDPNeighbor neighbor = new UDPNeighbor(new InetSocketAddress(HOST, PORT), udpSocket, true);
        neighbor.send(packet);
        verify(udpSocket).send(packet);
    }

    @Test
    public void whenSendExceptionThenCatchAndContinue() throws IOException {
        UDPNeighbor neighbor = new UDPNeighbor(new InetSocketAddress(HOST, 42), udpSocket, true);
        doThrow(new RuntimeException("test")).when(udpSocket).send(any(DatagramPacket.class));
        neighbor.send(packet);
    }

    @Test
    public void whenSendThenIncrementSentTransactions() throws IOException {
        UDPNeighbor neighbor = new UDPNeighbor(new InetSocketAddress(HOST, 42), udpSocket, true);
        long txCount = neighbor.getNumberOfSentTransactions();
        neighbor.send(packet);
        verify(udpSocket).send(packet);
        assertEquals(txCount + 1, neighbor.getNumberOfSentTransactions());
    }

}