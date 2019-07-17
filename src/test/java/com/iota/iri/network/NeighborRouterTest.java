package com.iota.iri.network;

import com.iota.iri.conf.IotaConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.network.pipeline.TransactionProcessingPipeline;
import com.iota.iri.network.protocol.Handshake;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NeighborRouterTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private IotaConfig nodeConfigA;
    @Mock
    private IotaConfig nodeConfigB;

    @Mock
    private TransactionRequester transactionRequester;

    @Mock
    private TransactionProcessingPipeline txPipeline;

    @Test
    public void initsWithConfigDefinedNeighbors() throws InterruptedException {
        List<String> configNeighbors = new ArrayList<>(Arrays.asList("tcp://127.0.0.1:11000", "tcp://127.0.0.1:12000"));
        Mockito.when(nodeConfigA.getNeighbors()).thenReturn(configNeighbors);
        Mockito.when(nodeConfigA.getNeighboringSocketAddress()).thenReturn("127.0.0.1");
        Mockito.when(nodeConfigA.getNeighboringSocketPort()).thenReturn(15600);
        Mockito.when(nodeConfigA.getCoordinator()).thenReturn(Hash.NULL_HASH);
        Mockito.when(nodeConfigA.getReconnectAttemptIntervalSeconds()).thenReturn(30);

        NeighborRouter neighborRouter = new NeighborRouterImpl(nodeConfigA, nodeConfigA, transactionRequester, txPipeline);

        Thread neighborRouterThread = new Thread(neighborRouter::route);
        neighborRouterThread.start();

        Thread.sleep(1000);

        List<Neighbor> neighbors = neighborRouter.getNeighbors();
        for (Neighbor neighbor : neighbors) {
            String uri = String.format("tcp://%s", neighbor.getHostAddressAndPort());
            assertTrue("should be in neighbors set from the config", configNeighbors.contains(uri));
        }

        neighborRouter.shutdown();
        neighborRouterThread.interrupt();
        neighborRouterThread.join();
    }

    @Test
    public void addAndRemoveNeighborsAddsAndRemovesConnectionsAccordingly() throws Exception {
        URI neighborAURI = URI.create("tcp://127.0.0.1:19000");
        String neighborAIdentity = String.format("%s:%d", neighborAURI.getHost(), neighborAURI.getPort());
        URI neighborBURI = URI.create("tcp://127.0.0.1:20000");

        List<String> configNeighborsA = new ArrayList<>();
        Mockito.when(nodeConfigA.isTestnet()).thenReturn(true);
        Mockito.when(nodeConfigA.getNeighbors()).thenReturn(configNeighborsA);
        Mockito.when(nodeConfigA.getNeighboringSocketAddress()).thenReturn("127.0.0.1");
        Mockito.when(nodeConfigA.getNeighboringSocketPort()).thenReturn(19000);
        Mockito.when(nodeConfigA.getMaxNeighbors()).thenReturn(1);
        Mockito.when(nodeConfigA.getCoordinator()).thenReturn(Hash.NULL_HASH);
        Mockito.when(nodeConfigA.getReconnectAttemptIntervalSeconds()).thenReturn(30);
        Mockito.when(nodeConfigA.isAutoTetheringEnabled()).thenReturn(true);
        Mockito.when(nodeConfigA.getMwm()).thenReturn(1);
        NeighborRouter neighborRouterA = new NeighborRouterImpl(nodeConfigA, nodeConfigA, transactionRequester, txPipeline);

        List<String> configNeighborsB = new ArrayList<>();
        Mockito.when(nodeConfigB.isTestnet()).thenReturn(true);
        Mockito.when(nodeConfigB.getNeighbors()).thenReturn(configNeighborsB);
        Mockito.when(nodeConfigB.getNeighboringSocketAddress()).thenReturn("127.0.0.1");
        Mockito.when(nodeConfigB.getNeighboringSocketPort()).thenReturn(20000);
        Mockito.when(nodeConfigB.getMaxNeighbors()).thenReturn(1);
        Mockito.when(nodeConfigB.getCoordinator()).thenReturn(Hash.NULL_HASH);
        Mockito.when(nodeConfigB.getReconnectAttemptIntervalSeconds()).thenReturn(30);
        Mockito.when(nodeConfigB.isAutoTetheringEnabled()).thenReturn(true);
        Mockito.when(nodeConfigB.getMwm()).thenReturn(1);
        NeighborRouter neighborRouterB = new NeighborRouterImpl(nodeConfigB, nodeConfigB, transactionRequester, txPipeline);

        Thread neighborRouterAThread = new Thread(neighborRouterA::route, "A");
        Thread neighborRouterBThread = new Thread(neighborRouterB::route, "B");
        neighborRouterAThread.start();
        neighborRouterBThread.start();

        Thread.sleep(2000);

        // A should not have any neighbors
        assertEquals("should not have any neighbors yet", 0, neighborRouterA.getConnectedNeighbors().size());

        // B should not have any neighbors
        assertEquals("should not have any neighbors yet", 0, neighborRouterB.getConnectedNeighbors().size());

        neighborRouterA.addNeighbor(neighborBURI.toString());

        Thread.sleep(2000);

        // should now be connected with each other
        assertEquals("neighbor B should be connected", 1, neighborRouterA.getConnectedNeighbors().size());
        assertEquals("neighbor A should be connected", 1, neighborRouterB.getConnectedNeighbors().size());

        // shutdown A
        neighborRouterA.removeNeighbor(neighborBURI.toString());

        // send something to A in order to let A remove the connection to B
        Neighbor neighborA = neighborRouterB.getConnectedNeighbors().get(neighborAIdentity);
        neighborA.send(
                Handshake.createHandshakePacket((char) 19000, Hash.NULL_HASH.bytes(), (byte) nodeConfigA.getMwm()));

        Thread.sleep(2000);

        assertEquals("should not have any connected neighbors anymore", 0,
                neighborRouterA.getConnectedNeighbors().size());
        assertEquals("should not have any connected neighbors anymore", 0,
                neighborRouterB.getConnectedNeighbors().size());

        neighborRouterA.shutdown();
        neighborRouterAThread.interrupt();
        neighborRouterAThread.join();

        neighborRouterB.shutdown();
        neighborRouterBThread.interrupt();
        neighborRouterBThread.join();
    }

}