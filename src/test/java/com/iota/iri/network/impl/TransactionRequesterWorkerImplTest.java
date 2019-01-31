package com.iota.iri.network.impl;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;

import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.controllers.TransactionViewModelTest;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.network.Node;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.network.impl.TransactionRequesterWorkerImpl;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;

public class TransactionRequesterWorkerImplTest {
    
    //Good
    private static final TransactionViewModel TVMRandomNull = new TransactionViewModel(
            getRandomTransaction(new Random()), Hash.NULL_HASH);
    private static final TransactionViewModel TVMRandomNotNull = new TransactionViewModel(
            getRandomTransaction(new Random()), TransactionViewModelTest.getRandomTransactionHash());
    private static final TransactionViewModel TVMAll9Null = new TransactionViewModel(
            get9Transaction(), Hash.NULL_HASH);
    
    //Bad
    private static final TransactionViewModel TVMAll9NotNull = new TransactionViewModel(
            get9Transaction(), TransactionViewModelTest.getRandomTransactionHash()); 
    
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
        
        //Set start queue to 0 for mocking
        setFinalStatic(worker.getClass().getDeclaredField("REQUESTER_THREAD_ACTIVATION_THRESHOLD"), 0);
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
        
        assertTrue(fakeWorker.processRequestQueue());
        assertTrue(fakeWorker.processRequestQueue());
        assertTrue(fakeWorker.processRequestQueue());
        assertFalse(fakeWorker.processRequestQueue());
    }
    
    @Test
    public void validTipToAddTest() throws Exception {
       assertTrue(worker.isValidTransaction(TVMRandomNull));
       assertTrue(worker.isValidTransaction(TVMRandomNotNull));
       assertTrue(worker.isValidTransaction(TVMAll9Null));
       
       assertFalse(worker.isValidTransaction(TVMAll9NotNull));
    }
    
    private static Transaction getRandomTransaction(Random seed) {
        Transaction transaction = TransactionViewModelTest.getRandomTransaction(seed);
        
        transaction.type = TransactionViewModel.FILLED_SLOT;
        transaction.parsed = true;
        return transaction;
    }
    
    private static Transaction get9Transaction() {
        Transaction transaction = new Transaction();

        byte[] trits = new byte[TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE];
        Arrays.fill(trits, (byte) 1);

        transaction.bytes = Converter.allocateBytesForTrits(trits.length);
        Converter.bytes(trits, 0, transaction.bytes, 0, trits.length);
        return transaction;
    }
    
    private static void setFinalStatic(Field field, Object newValue) throws Exception {
        field.setAccessible(true);        
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, newValue);
    }
}
