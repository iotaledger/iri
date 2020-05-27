package com.iota.iri.network.pipeline;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.network.NeighborRouter;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.network.neighbor.impl.NeighborImpl;
import com.iota.iri.service.milestone.InSyncService;
import com.iota.iri.service.validation.TransactionSolidifier;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.HashMap;
import java.util.Map;

public class BroadcastStageTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private TransactionSolidifier transactionSolidifier;

    @Mock
    private NeighborRouter neighborRouter;
    
    private InSyncService inSyncService = new InSyncService() {
        
        @Override
        public boolean isInSync() {
            return true;
        }
    };

    // setup neighbors
    private final static Neighbor neighborA = new NeighborImpl<>(null, null, "A", 0, null);
    private final static Neighbor neighborB = new NeighborImpl<>(null, null, "B", 0, null);
    private static Map<String, Neighbor> neighbors = new HashMap<>();

    static {
        neighbors.put(neighborA.getHostAddress(), neighborA);
        neighbors.put(neighborB.getHostAddress(), neighborB);
    }

    @Test
    public void doesntGossipToOriginNeighbor() {
        Mockito.when(neighborRouter.getConnectedNeighbors()).thenReturn(neighbors);

        BroadcastStage broadcastStage = new BroadcastStage(neighborRouter, transactionSolidifier, inSyncService);
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

        BroadcastStage broadcastStage = new BroadcastStage(neighborRouter, transactionSolidifier, inSyncService);
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

}