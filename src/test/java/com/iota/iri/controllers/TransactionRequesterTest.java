package com.iota.iri.controllers;

import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.impl.SnapshotProviderImpl;
import com.iota.iri.storage.Tangle;
import com.iota.iri.zmq.MessageQ;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by paul on 5/2/17.
 */
public class TransactionRequesterTest {
    private static Tangle tangle = new Tangle();
    private static SnapshotProvider snapshotProvider;
    private MessageQ mq;

    @Before
    public void setUp() throws Exception {
        snapshotProvider = new SnapshotProviderImpl().init(new MainnetConfig());
    }

    @After
    public void tearDown() throws Exception {
        snapshotProvider.shutdown();
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
    public void nonMilestoneCapacityLimited() throws Exception {
        TransactionRequester txReq = new TransactionRequester(tangle, snapshotProvider, mq);
        int capacity = TransactionRequester.MAX_TX_REQ_QUEUE_SIZE;
        //fill tips list
        for (int i = 0; i < capacity * 2 ; i++) {
            Hash hash = TransactionViewModelTest.getRandomTransactionHash();
            txReq.requestTransaction(hash,false);
        }
        //check that limit wasn't breached
        assertEquals(capacity, txReq.numberOfTransactionsToRequest());
    }

    @Test
    public void milestoneCapacityNotLimited() throws Exception {
        TransactionRequester txReq = new TransactionRequester(tangle, snapshotProvider, mq);
        int capacity = TransactionRequester.MAX_TX_REQ_QUEUE_SIZE;
        //fill tips list
        for (int i = 0; i < capacity * 2 ; i++) {
            Hash hash = TransactionViewModelTest.getRandomTransactionHash();
            txReq.requestTransaction(hash,true);
        }
        //check that limit was surpassed
        assertEquals(capacity * 2, txReq.numberOfTransactionsToRequest());
    }

    @Test
    public void mixedCapacityLimited() throws Exception {
        TransactionRequester txReq = new TransactionRequester(tangle, snapshotProvider, mq);
        int capacity = TransactionRequester.MAX_TX_REQ_QUEUE_SIZE;
        //fill tips list
        for (int i = 0; i < capacity * 4 ; i++) {
            Hash hash = TransactionViewModelTest.getRandomTransactionHash();
            txReq.requestTransaction(hash, (i % 2 == 1));

        }
        //check that limit wasn't breached
        assertEquals(capacity + capacity * 2, txReq.numberOfTransactionsToRequest());
    }

}
