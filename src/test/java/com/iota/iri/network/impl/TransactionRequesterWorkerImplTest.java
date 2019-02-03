package com.iota.iri.network.impl;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import static org.mockito.Mockito.*;

import java.util.LinkedList;

import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.Node;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.network.impl.TransactionRequesterWorkerImpl;
import com.iota.iri.storage.Tangle;

import static com.iota.iri.TransactionTestUtils.*;

public class TransactionRequesterWorkerImplTest {
    
    //Good
    private static final TransactionViewModel TVMRandomNull = new TransactionViewModel(
            TransactionTestUtils.getRandomTransaction(), Hash.NULL_HASH);
    private static final TransactionViewModel TVMRandomNotNull = new TransactionViewModel(
            TransactionTestUtils.getRandomTransaction(), getRandomTransactionHash());
    private static final TransactionViewModel TVMAll9Null = new TransactionViewModel(
            TransactionTestUtils.get9Transaction(), Hash.NULL_HASH);
    
    //Bad
    private static final TransactionViewModel TVMAll9NotNull = new TransactionViewModel(
            TransactionTestUtils.get9Transaction(), getRandomTransactionHash()); 
    
    private static Tangle tangle;
    private static Node node;
    private static TipsViewModel tvm;
    
    private static TransactionRequester requester;
    private static TransactionRequesterWorkerImpl worker;
    
    @Rule 
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    
    @BeforeClass
    public static void setUp(){
        requester = mock(TransactionRequester.class);
        requester.init(0); //Dont drop anything
    }

    @Before
    public void before() throws NoSuchFieldException, SecurityException, Exception {
        tangle = mock(Tangle.class);
        tvm = mock(TipsViewModel.class);
        
        worker = new TransactionRequesterWorkerImpl();
        
        node = mock(Node.class);
        when(node.getNeighbors()).thenReturn(new LinkedList<>());
        
        worker.init(tangle, requester, tvm, node);
    }
    
    @AfterClass
    public static void tearDown() throws Exception {
        worker.shutdown();
    }
    
    @Test
    public void processRequestQueueTest() throws Exception {
        TransactionRequesterWorkerImpl fakeWorker = mock(TransactionRequesterWorkerImpl.class);
        
        when(fakeWorker.isActive()).thenReturn(true);
        when(fakeWorker.getTransactionToSendWithRequest())
            .thenReturn(TVMRandomNull)
            .thenReturn(TVMRandomNotNull)
            .thenReturn(TVMAll9Null)
            .thenReturn(TVMAll9NotNull);
        
        when(fakeWorker.init(any(), any(), any(), any())).thenCallRealMethod();
        fakeWorker.init(tangle, requester, tvm, node);
        
        when(fakeWorker.isValidTransaction(any())).thenCallRealMethod();
        when(fakeWorker.processRequestQueue()).thenCallRealMethod();
        
        assertTrue("Null transaction hash should be processed", fakeWorker.processRequestQueue());
        assertTrue("Not null transaction hash should be processed", fakeWorker.processRequestQueue());
        assertTrue("Null transaction hash should be processed", fakeWorker.processRequestQueue());
        assertFalse("All 9s transaction should not be processed", fakeWorker.processRequestQueue());
    }
    
    @Test
    public void validTipToAddTest() throws Exception {
       assertTrue("Null transaction hash should always be accepted", worker.isValidTransaction(TVMRandomNull));
       assertTrue("Not null transaction hash should always be accepted", worker.isValidTransaction(TVMRandomNotNull));
       assertTrue("Null transaction hash should always be accepted", worker.isValidTransaction(TVMAll9Null));
       
       assertFalse("All 9s transaction should not be accepted", worker.isValidTransaction(TVMAll9NotNull));
    }
}
