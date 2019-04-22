package com.iota.iri.network;

import com.iota.iri.conf.IotaConfig;
import com.iota.iri.conf.NodeConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.network.pipeline.TransactionProcessingPipeline;
import com.iota.iri.network.protocol.Protocol;
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
import java.util.Map;

import static org.junit.Assert.*;

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
        NeighborRouter neighborRouter = new NeighborRouter();

        List<String> configNeighbors = new ArrayList<>(Arrays.asList("tcp://127.0.0.1:11000", "tcp://127.0.0.1:12000"));
        Mockito.when(nodeConfigA.getNeighbors()).thenReturn(configNeighbors);
        Mockito.when(nodeConfigA.getNeighboringSocketAddress()).thenReturn("127.0.0.1");
        Mockito.when(nodeConfigA.getNeighboringSocketPort()).thenReturn(15600);
        Mockito.when(nodeConfigA.getCoordinator()).thenReturn(Hash.NULL_HASH.toString());
        Mockito.when(nodeConfigA.getReconnectAttemptIntervalSeconds()).thenReturn(30);

        neighborRouter.init(nodeConfigA, transactionRequester, txPipeline);

        Thread neighborRouterThread = new Thread(neighborRouter::route);
        neighborRouterThread.start();

        Thread.sleep(1000);

        List<Neighbor> neighbors = neighborRouter.getNeighbors();
        for (Neighbor neighbor : neighbors) {
            String uri = String.format("tcp://%s", neighbor.getHostAddressAndPort());
            assertTrue(configNeighbors.contains(uri));
        }

        neighborRouter.shutdown();
        neighborRouterThread.interrupt();
        neighborRouterThread.join();
    }

    @Test
    public void establishesConnsFromConfigAndDropsThemAccordingly() throws Exception {
        NeighborRouter neighborRouterA = new NeighborRouter();
        NeighborRouter neighborRouterB = new NeighborRouter();

        URI neighborAURI = URI.create("tcp://127.0.0.1:15000");
        String neighborAIdentity = String.format("%s:%d", neighborAURI.getHost(), neighborAURI.getPort());
        URI neighborBURI = URI.create("tcp://127.0.0.1:16000");
        String neighborBIdentity = String.format("%s:%d", neighborBURI.getHost(), neighborBURI.getPort());

        List<String> configNeighborsA = new ArrayList<>(Arrays.asList(neighborBURI.toString()));
        Mockito.when(nodeConfigA.isTestnet()).thenReturn(true);
        Mockito.when(nodeConfigA.getNeighbors()).thenReturn(configNeighborsA);
        Mockito.when(nodeConfigA.getNeighboringSocketAddress()).thenReturn("127.0.0.1");
        Mockito.when(nodeConfigA.getNeighboringSocketPort()).thenReturn(15000);
        Mockito.when(nodeConfigA.getMaxNeighbors()).thenReturn(1);
        Mockito.when(nodeConfigA.getCoordinator()).thenReturn(Hash.NULL_HASH.toString());
        Mockito.when(nodeConfigA.getReconnectAttemptIntervalSeconds()).thenReturn(30);
        neighborRouterA.init(nodeConfigA, transactionRequester, txPipeline);

        List<String> configNeighborsB = new ArrayList<>(Arrays.asList(neighborAURI.toString()));
        Mockito.when(nodeConfigB.isTestnet()).thenReturn(true);
        Mockito.when(nodeConfigB.getNeighbors()).thenReturn(configNeighborsB);
        Mockito.when(nodeConfigB.getNeighboringSocketAddress()).thenReturn("127.0.0.1");
        Mockito.when(nodeConfigB.getNeighboringSocketPort()).thenReturn(16000);
        Mockito.when(nodeConfigB.getMaxNeighbors()).thenReturn(1);
        Mockito.when(nodeConfigB.getCoordinator()).thenReturn(Hash.NULL_HASH.toString());
        Mockito.when(nodeConfigB.getReconnectAttemptIntervalSeconds()).thenReturn(30);
        neighborRouterB.init(nodeConfigB, transactionRequester, txPipeline);

        Thread neighborRouterAThread = new Thread(neighborRouterA::route, "A");
        Thread neighborRouterBThread = new Thread(neighborRouterB::route, "B");
        neighborRouterAThread.start();
        neighborRouterBThread.start();

        Thread.sleep(1000);

        // A should be connected to B
        Map<String, Neighbor> connectedNeighborsA = neighborRouterA.getConnectedNeighbors();
        assertEquals(1, connectedNeighborsA.size());
        assertTrue(connectedNeighborsA.containsKey(neighborBIdentity));

        // B should be connected to A
        Map<String, Neighbor> connectedNeighborsB = neighborRouterB.getConnectedNeighbors();
        assertEquals(1, connectedNeighborsB.size());
        assertTrue(connectedNeighborsB.containsKey(neighborAIdentity));

        // shutdown A
        neighborRouterA.shutdown();
        neighborRouterAThread.interrupt();
        neighborRouterAThread.join();

        // send something to A in order to let B know that A is disconnected
        Neighbor neighborA = neighborRouterB.getConnectedNeighbors().get(neighborAIdentity);
        neighborA.send(Protocol.createHandshakePacket((char) 16000, Hash.NULL_HASH.bytes()));

        // should now be disconnected
        Thread.sleep(1000);
        assertEquals(0, connectedNeighborsB.size());

        neighborRouterB.shutdown();
        neighborRouterBThread.interrupt();
        neighborRouterBThread.join();
    }

    @Test
    public void addAndRemoveNeighborsAddsAndRemovesConnectionsAccordingly() throws Exception {
        NeighborRouter neighborRouterA = new NeighborRouter();
        NeighborRouter neighborRouterB = new NeighborRouter();

        URI neighborAURI = URI.create("tcp://127.0.0.1:15000");
        String neighborAIdentity = String.format("%s:%d", neighborAURI.getHost(), neighborAURI.getPort());
        URI neighborBURI = URI.create("tcp://127.0.0.1:16000");
        String neighborBIdentity = String.format("%s:%d", neighborBURI.getHost(), neighborBURI.getPort());

        List<String> configNeighborsA = new ArrayList<>();
        Mockito.when(nodeConfigA.isTestnet()).thenReturn(true);
        Mockito.when(nodeConfigA.getNeighbors()).thenReturn(configNeighborsA);
        Mockito.when(nodeConfigA.getNeighboringSocketAddress()).thenReturn("127.0.0.1");
        Mockito.when(nodeConfigA.getNeighboringSocketPort()).thenReturn(15000);
        Mockito.when(nodeConfigA.getMaxNeighbors()).thenReturn(1);
        Mockito.when(nodeConfigA.getCoordinator()).thenReturn(Hash.NULL_HASH.toString());
        Mockito.when(nodeConfigA.getReconnectAttemptIntervalSeconds()).thenReturn(30);
        neighborRouterA.init(nodeConfigA, transactionRequester, txPipeline);

        List<String> configNeighborsB = new ArrayList<>();
        Mockito.when(nodeConfigB.isTestnet()).thenReturn(true);
        Mockito.when(nodeConfigB.getNeighbors()).thenReturn(configNeighborsB);
        Mockito.when(nodeConfigB.getNeighboringSocketAddress()).thenReturn("127.0.0.1");
        Mockito.when(nodeConfigB.getNeighboringSocketPort()).thenReturn(16000);
        Mockito.when(nodeConfigB.getMaxNeighbors()).thenReturn(1);
        Mockito.when(nodeConfigB.getCoordinator()).thenReturn(Hash.NULL_HASH.toString());
        Mockito.when(nodeConfigB.getReconnectAttemptIntervalSeconds()).thenReturn(30);
        neighborRouterB.init(nodeConfigB, transactionRequester, txPipeline);

        Thread neighborRouterAThread = new Thread(neighborRouterA::route, "A");
        Thread neighborRouterBThread = new Thread(neighborRouterB::route, "B");
        neighborRouterAThread.start();
        neighborRouterBThread.start();

        Thread.sleep(1000);

        // A should not have any neighbors
        assertEquals(0, neighborRouterA.getConnectedNeighbors().size());

        // B should not have any neighbors
        assertEquals(0, neighborRouterB.getConnectedNeighbors().size());

        neighborRouterA.addNeighbor(neighborBURI.toString());

        Thread.sleep(1000);

        // should now be connected with each other
        assertEquals(1, neighborRouterA.getConnectedNeighbors().size());
        assertEquals(1, neighborRouterB.getConnectedNeighbors().size());

        // shutdown A
        neighborRouterA.removeNeighbor(neighborBURI.toString());

        // send something to A in order to let A remove the connection to B
        Neighbor neighborA = neighborRouterB.getConnectedNeighbors().get(neighborAIdentity);
        neighborA.send(Protocol.createHandshakePacket((char) 16000, Hash.NULL_HASH.bytes()));

        Thread.sleep(1000);

        assertEquals(0, neighborRouterA.getConnectedNeighbors().size());
        assertEquals(0, neighborRouterB.getConnectedNeighbors().size());

        neighborRouterA.shutdown();
        neighborRouterAThread.interrupt();
        neighborRouterAThread.join();

        neighborRouterB.shutdown();
        neighborRouterBThread.interrupt();
        neighborRouterBThread.join();
    }

}