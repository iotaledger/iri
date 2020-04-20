package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.network.FakeChannel;
import com.iota.iri.network.NeighborRouter;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.network.neighbor.NeighborState;
import com.iota.iri.network.neighbor.impl.NeighborImpl;

import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Map;

import com.iota.iri.network.protocol.Heartbeat;
import com.iota.iri.service.validation.TransactionSolidifier;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class BroadcastStageTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private TransactionSolidifier transactionSolidifier;

    @Mock
    private NeighborRouter neighborRouter;

    @Mock
    private Selector selector;

    private final static int firstProtocolVersion = 1;
    private final static int stingProtocolVersion = 2;

    // setup neighbors
    private final static Neighbor neighborA = Mockito.spy(new NeighborImpl<>(null, null, "A", 0, null));
    private final static Neighbor neighborB = Mockito.spy(new NeighborImpl<>(null, null, "B", 0, null));
    private static Map<String, Neighbor> neighbors = new HashMap<>();

    static {
        neighbors.put(neighborA.getHostAddress(), neighborA);
        neighbors.put(neighborB.getHostAddress(), neighborB);
    }

    @Test
    public void doesntGossipToOriginNeighbor() {
        Mockito.when(neighborRouter.getConnectedNeighbors()).thenReturn(neighbors);

        BroadcastStage broadcastStage = new BroadcastStage(neighborRouter, transactionSolidifier);
        TransactionViewModel tvm = new TransactionViewModel(new Transaction(), null);
        BroadcastPayload broadcastPayload = new BroadcastPayload(neighborA, tvm);
        ProcessingContext ctx = new ProcessingContext(null, broadcastPayload);
        broadcastStage.process(ctx);

        try {
            // should not have send the tvm to the origin neighbor
            Mockito.verify(neighborRouter, Mockito.never()).gossipTransactionTo(neighborA, tvm);
            // should send it to the other neighbors
            Mockito.verify(neighborRouter).gossipTransactionTo(neighborB, tvm);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void gossipsToAllIfNoOriginNeighbor() {
        Mockito.when(neighborRouter.getConnectedNeighbors()).thenReturn(neighbors);

        BroadcastStage broadcastStage = new BroadcastStage(neighborRouter, transactionSolidifier);
        TransactionViewModel tvm = new TransactionViewModel(new Transaction(), null);
        BroadcastPayload broadcastPayload = new BroadcastPayload(null, tvm);
        ProcessingContext ctx = new ProcessingContext(null, broadcastPayload);
        broadcastStage.process(ctx);

        try {
            Mockito.verify(neighborRouter).gossipTransactionTo(neighborA, tvm);
            Mockito.verify(neighborRouter).gossipTransactionTo(neighborB, tvm);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void gossipToNeighborAccordingToHeartbeat() throws Exception {
        TransactionViewModel tvm = Mockito.mock(TransactionViewModel.class);
        Mockito.when(tvm.snapshotIndex()).thenReturn(90);

        Heartbeat heartbeat = new Heartbeat();
        heartbeat.setLastSolidMilestoneIndex(100);
        heartbeat.setFirstSolidMilestoneIndex(50);
        Neighbor neighborC = Mockito.spy(new NeighborImpl<>(selector, new FakeChannel() {
            @Override
            public int read(ByteBuffer dst) {
                return 0;
            }
        }, "C", 0, null));

        neighborC.setState(NeighborState.READY_FOR_MESSAGES);
        Mockito.when(neighborB.getProtocolVersion()).thenReturn(firstProtocolVersion);
        Mockito.when(neighborC.getProtocolVersion()).thenReturn(stingProtocolVersion);
        Mockito.when(neighborC.heartbeat()).thenReturn(heartbeat);
        neighbors.put(neighborC.getHostAddress(), neighborC);
        Mockito.when(neighborRouter.getConnectedNeighbors()).thenReturn(neighbors);

        BroadcastStage broadcastStage = new BroadcastStage(neighborRouter, transactionSolidifier);
        BroadcastPayload broadcastPayload = new BroadcastPayload(neighborA, tvm);
        ProcessingContext ctx = new ProcessingContext(null, broadcastPayload);
        broadcastStage.process(ctx);

        // should send the tvm as neighbor heartbeat is in the milestone range.
        Mockito.verify(neighborRouter).gossipTransactionTo(neighborC, tvm);
    }

    @Test
    public void shouldNotGossipToNeighborAccordingToHeartbeat() throws Exception {
        TransactionViewModel tvm = Mockito.mock(TransactionViewModel.class);
        Mockito.when(tvm.snapshotIndex()).thenReturn(40);

        Heartbeat heartbeat = new Heartbeat();
        heartbeat.setLastSolidMilestoneIndex(100);
        heartbeat.setFirstSolidMilestoneIndex(50);
        Neighbor neighborC = Mockito.spy(new NeighborImpl<>(selector, new FakeChannel() {
            @Override
            public int read(ByteBuffer dst) {
                return 0;
            }
        }, "C", 0, null));

        neighborC.setState(NeighborState.READY_FOR_MESSAGES);
        Mockito.when(neighborB.getProtocolVersion()).thenReturn(firstProtocolVersion);
        Mockito.when(neighborC.getProtocolVersion()).thenReturn(stingProtocolVersion);
        Mockito.when(neighborC.heartbeat()).thenReturn(heartbeat);
        neighbors.put(neighborC.getHostAddress(), neighborC);
        Mockito.when(neighborRouter.getConnectedNeighbors()).thenReturn(neighbors);

        BroadcastStage broadcastStage = new BroadcastStage(neighborRouter, transactionSolidifier);
        BroadcastPayload broadcastPayload = new BroadcastPayload(neighborA, tvm);
        ProcessingContext ctx = new ProcessingContext(null, broadcastPayload);
        broadcastStage.process(ctx);

        // should not send the tvm as neighbor heartbeat is not in the milestone range.
        Mockito.verify(neighborRouter, Mockito.never()).gossipTransactionTo(neighborC, tvm);
    }
}