package com.iota.iri.utils.dag;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.iota.iri.TangleMockUtils;
import com.iota.iri.TransactionTestUtils;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Approvee;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.storage.Tangle;

public class DAGHelperTest {
    
    private static final Hash A = TransactionTestUtils.getRandomTransactionHash();
    private static final Hash B = TransactionTestUtils.getRandomTransactionHash();
    private static final Hash C = TransactionTestUtils.getRandomTransactionHash();
    private static final Hash D = TransactionTestUtils.getRandomTransactionHash();
    
    private static final Transaction TX1 = TransactionTestUtils
            .createRandomTransactionWithTrunkAndBranch(Hash.NULL_HASH, Hash.NULL_HASH); // Hash.NULL_HASH, 
    private static final Transaction TX2 = TransactionTestUtils
            .createRandomTransactionWithTrunkAndBranch(Hash.NULL_HASH, B); //A
    private static final Transaction TX3 = TransactionTestUtils
            .createRandomTransactionWithTrunkAndBranch( A, B); //C
    
    @Rule 
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    
    @Mock
    private Tangle tangle;

    private DAGHelper helper;

    @Before
    public void setUp() throws Exception {
        helper = DAGHelper.get(tangle);
    }

    @Test
    public void testGet() {
        // We reuse the instanced
        assertEquals(helper, DAGHelper.get(tangle));
    }

    @Test
    public void testTraverseApprovers() throws Exception {
        TangleMockUtils.mockTransaction(tangle, Hash.NULL_HASH, TX1);
        TangleMockUtils.mockTransaction(tangle, A, TX2);
        TangleMockUtils.mockTransaction(tangle, C, TX3);
        
        Mockito.when(tangle.load(Approvee.class, Hash.NULL_HASH)).thenReturn(new Approvee(A));
        Mockito.when(tangle.load(Approvee.class, A)).thenReturn(new Approvee(C));
        
        List<TransactionViewModel> processed = new LinkedList<>();
        Set<Hash> set = new HashSet<>();
        
        helper.traverseApprovers(Hash.NULL_HASH, transaction -> {
            return true;
        }, t -> {
            processed.add(t);
        }, set);
        
        assertTrue(processed.size() == 2);
        
        TransactionViewModel tx = processed.get(1);
        assertEquals(tx.getHash(), C);
        assertEquals(tx.getAddressHash(), TX3.address);
        assertEquals(tx.getAttachmentTimestamp(), TX3.attachmentTimestamp);
        assertEquals(tx.getBytes(), TX3.bytes());
    }
        

    @Test
    public void testTraverseApprovees() throws Exception {
        TangleMockUtils.mockTransaction(tangle, Hash.NULL_HASH, TX1);
        TangleMockUtils.mockTransaction(tangle, A, TX2);
        TangleMockUtils.mockTransaction(tangle, C, TX3);
        
        List<TransactionViewModel> processed = new LinkedList<>();
        Set<Hash> set = new HashSet<>();
        
        helper.traverseApprovees(C, transaction -> {
            return true;
        }, t -> {
            processed.add(t);
        }, set);
        
        assertTrue(processed.size() == 2);
        
        TransactionViewModel tx = processed.get(1);
        assertEquals(tx.getHash(), Hash.NULL_HASH);
        assertEquals(tx.getAddressHash(), TX1.address);
        assertEquals(tx.getAttachmentTimestamp(), TX1.attachmentTimestamp);
        assertEquals(tx.getBytes(), TX1.bytes());
    }

}
