package com.iota.iri.network.impl;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.iota.iri.TangleMockUtils;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.network.Node;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.network.impl.TransactionRequesterWorkerImpl;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.zmq.MessageQ;

import static com.iota.iri.TransactionTestUtils.getRandomTransaction;
import static com.iota.iri.TransactionTestUtils.get9Transaction;
import static com.iota.iri.TransactionTestUtils.buildTransaction;
import static com.iota.iri.TransactionTestUtils.getRandomTransactionHash;

import static org.mockito.Mockito.when;

public class TransactionRequesterWorkerImplTest {
    
    //Good
    private static final TransactionViewModel TVMRandomNull = new TransactionViewModel(
            getRandomTransaction(), Hash.NULL_HASH);
    private static final TransactionViewModel TVMRandomNotNull = new TransactionViewModel(
            getRandomTransaction(), getRandomTransactionHash());
    private static final TransactionViewModel TVMAll9Null = new TransactionViewModel(
            get9Transaction(), Hash.NULL_HASH);
    private static final TransactionViewModel TVMAll9NotNull = new TransactionViewModel(
            get9Transaction(), getRandomTransactionHash()); 
    
    //Bad
    private static final TransactionViewModel TVMNullNull = new TransactionViewModel((Transaction)null, Hash.NULL_HASH); 
    
    @Rule 
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private static SnapshotProvider snapshotProvider;
    
    @Mock
    private static MessageQ messageQ;
    
    private static TransactionRequester requester;
    private static TransactionRequesterWorkerImpl worker;

    @Mock
    private Tangle tangle;
    
    @Mock
    private Node node;
    
    @Mock
    private TipsViewModel tipsVM;

    @Before
    public void before() {
        requester = new TransactionRequester(tangle, snapshotProvider, messageQ);
        
        worker = new TransactionRequesterWorkerImpl();
        worker.init(tangle, requester, tipsVM, node);
    }
    
    @After
    public void tearDown() {
        worker.shutdown();
    }
    
    @Test
    public void workerActive() throws Exception {
        assertFalse("Empty worker should not be active", worker.isActive());
        
        fillRequester();
        
        assertTrue("Worker should be active when it requester is over threshold", worker.isActive());
    }
    
    @Test
    public void processRequestQueueTest() throws Exception {
        //getTransactionToSendWithRequest starts reading from solid tips, so mock data from that call
        when(tipsVM.getRandomSolidTipHash()).thenReturn(
                TVMRandomNull.getHash(), 
                TVMRandomNotNull.getHash(), 
                TVMAll9Null.getHash(), 
                TVMAll9NotNull.getHash(),
                TVMNullNull.getHash(),
                null);
        
        assertFalse("Unfilled queue shouldnt process", worker.processRequestQueue());
        
        //Requester never goes down since nodes don't really request
        fillRequester();
        
        TangleMockUtils.mockTransaction(tangle, TVMRandomNull.getHash(), buildTransaction(TVMRandomNull.trits()));
        assertTrue("Null transaction hash should be processed", worker.processRequestQueue());
        
        TangleMockUtils.mockTransaction(tangle, TVMRandomNotNull.getHash(), buildTransaction(TVMRandomNotNull.trits()));
        assertTrue("Not null transaction hash should be processed", worker.processRequestQueue());
       
        TangleMockUtils.mockTransaction(tangle, TVMAll9Null.getHash(), buildTransaction(TVMAll9Null.trits()));
        assertTrue("Null transaction hash should be processed", worker.processRequestQueue());
        
        TangleMockUtils.mockTransaction(tangle, TVMAll9NotNull.getHash(), buildTransaction(TVMAll9NotNull.trits()));
        assertTrue("All 9s transaction should be processed", worker.processRequestQueue());
        
        // Null gets loaded as all 0, so type is 0 -> Filled
        TangleMockUtils.mockTransaction(tangle, TVMNullNull.getHash(), null);
        assertTrue("0 transaction should be processed", worker.processRequestQueue());
        
        // null -> NULL_HASH -> gets loaded as all 0 -> filled
        assertTrue("Null transaction should be processed", worker.processRequestQueue());
    }

    @Test
    public void validTipToAddTest() throws Exception {
       assertTrue("Null transaction hash should always be accepted", worker.isValidTransaction(TVMRandomNull));
       assertTrue("Not null transaction hash should always be accepted", worker.isValidTransaction(TVMRandomNotNull));
       assertTrue("Null transaction hash should always be accepted", worker.isValidTransaction(TVMAll9Null));
       assertTrue("All 9s transaction should be accepted", worker.isValidTransaction(TVMAll9NotNull));
       
       // Null gets loaded as all 0, so type is 0 -> Filled
       assertTrue("0 transaction should be accepted", worker.isValidTransaction(TVMNullNull));
       
       assertFalse("Null transaction should not be accepted", worker.isValidTransaction(null));
    }
    
    private void fillRequester() throws Exception {
        for (int i=0; i< TransactionRequesterWorkerImpl.REQUESTER_THREAD_ACTIVATION_THRESHOLD; i++) {
            addRequest();
        }
    }
    
    private void addRequest() throws Exception {
        Hash randomHash = getRandomTransactionHash();
        TangleMockUtils.mockTransaction(tangle, randomHash);
        requester.requestTransaction(randomHash, false);
    }
}
