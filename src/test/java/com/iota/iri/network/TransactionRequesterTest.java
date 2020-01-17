package com.iota.iri.network;

import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.impl.SnapshotMockUtils;
import com.iota.iri.storage.Tangle;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static com.iota.iri.TransactionTestUtils.getTransactionHash;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class TransactionRequesterTest {
    private static Tangle tangle = new Tangle();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private SnapshotProvider snapshotProvider;

    @Before
    public void setUp() throws Exception {
        Mockito.when(snapshotProvider.getInitialSnapshot()).thenReturn(SnapshotMockUtils.createSnapshot());
    }

    @Test
    public void init() throws Exception {

    }

    @Test
    public void rescanTransactionsToRequest() throws Exception {

    }

    @Test
    public void getRequestedTransactions() throws Exception {

    }

    @Test
    public void numberOfTransactionsToRequest() throws Exception {

    }

    @Test
    public void clearTransactionRequest() throws Exception {

    }

    @Test
    public void requestTransaction() throws Exception {

    }

    @Test
    public void transactionToRequest() throws Exception {

    }

    @Test
    public void checkSolidity() throws Exception {

    }

    @Test
    public void instance() throws Exception {

    }

    @Test
    public void popEldestTransactionToRequest() throws Exception {
        TransactionRequester txReq = new TransactionRequester(tangle, snapshotProvider);
        // Add some Txs to the pool and see if the method pops the eldest one
        Hash eldest = getTransactionHash();
        txReq.requestTransaction(eldest);
        txReq.requestTransaction(getTransactionHash());
        txReq.requestTransaction(getTransactionHash());
        txReq.requestTransaction(getTransactionHash());

        txReq.popEldestTransactionToRequest();
        // Check that the transaction is there no more
        assertFalse(txReq.isTransactionRequested(eldest));
    }

    @Test
    public void transactionRequestedFreshness() throws Exception {
        // Add some Txs to the pool and see if the method pops the eldest one
        List<Hash> eldest = new ArrayList<Hash>(Arrays.asList(
                getTransactionHash(),
                getTransactionHash(),
                getTransactionHash()
        ));
        TransactionRequester txReq = new TransactionRequester(tangle, snapshotProvider);
        int capacity = TransactionRequester.MAX_TX_REQ_QUEUE_SIZE;
        //fill tips list
        for (int i = 0; i < 3; i++) {
            txReq.requestTransaction(eldest.get(i));
        }
        for (int i = 0; i < capacity; i++) {
            Hash hash = getTransactionHash();
            txReq.requestTransaction(hash);
        }

        //check that limit wasn't breached
        assertEquals("Queue capacity breached!!", capacity, txReq.numberOfTransactionsToRequest());
        // None of the eldest transactions should be in the pool
        for (int i = 0; i < 3; i++) {
            assertFalse("Old transaction has been requested", txReq.isTransactionRequested(eldest.get(i)));
        }
    }

    @Test
    public void nonMilestoneCapacityLimited() throws Exception {
        TransactionRequester txReq = new TransactionRequester(tangle, snapshotProvider);
        int capacity = TransactionRequester.MAX_TX_REQ_QUEUE_SIZE;
        //fill tips list
        for (int i = 0; i < capacity * 2 ; i++) {
            Hash hash = getTransactionHash();
            txReq.requestTransaction(hash);
        }
        //check that limit wasn't breached
        assertEquals(capacity, txReq.numberOfTransactionsToRequest());
    }

}
