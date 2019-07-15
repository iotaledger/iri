package com.iota.iri.utils.dag;

import static org.junit.Assert.assertEquals;

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
    
    private static final Hash A = TransactionTestUtils.getTransactionHash();
    private static final Hash B = TransactionTestUtils.getTransactionHash();
    private static final Hash C = TransactionTestUtils.getTransactionHash();
    
    private static final Transaction TX1 = TransactionTestUtils
            .createTransactionWithTrunkAndBranch(Hash.NULL_HASH, Hash.NULL_HASH); // Hash.NULL_HASH, 
    private static final Transaction TX2 = TransactionTestUtils
            .createTransactionWithTrunkAndBranch(Hash.NULL_HASH, B); //A
    private static final Transaction TX3 = TransactionTestUtils
            .createTransactionWithTrunkAndBranch( A, B); //C
    
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
        assertEquals("Helper instance should have been reused", helper, DAGHelper.get(tangle));
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
        
        helper.traverseApprovers(Hash.NULL_HASH, transaction -> true,
                t -> {
                    processed.add(t);
                }, set);
        
        assertEquals("2 transactions should have been traversed", 2, processed.size());
        
        TransactionViewModel tx = processed.get(1);
        assertEquals("Last transaction hash should have been C", tx.getHash(), C);
        assertEquals("Last transaction should have TX3 its address", tx.getAddressHash(), TX3.address);
        assertEquals("Last transaction should have TX3 its timestamp", tx.getAttachmentTimestamp(), TX3.attachmentTimestamp);
        assertEquals("Last transaction should have TX3 its bytes", tx.getBytes(), TX3.bytes());
    }
        

    @Test
    public void testTraverseApprovees() throws Exception {
        TangleMockUtils.mockTransaction(tangle, Hash.NULL_HASH, TX1);
        TangleMockUtils.mockTransaction(tangle, A, TX2);
        TangleMockUtils.mockTransaction(tangle, C, TX3);
        
        List<TransactionViewModel> processed = new LinkedList<>();
        Set<Hash> set = new HashSet<>();
        
        helper.traverseApprovees(C, transaction -> true,
                t -> {
                    processed.add(t);
                }, set);
        
        assertEquals("2 transactions should have been traversed", 2, processed.size());
        
        TransactionViewModel tx = processed.get(1);
        assertEquals("Last transaction hash should have been the genisis hash", tx.getHash(), Hash.NULL_HASH);
        assertEquals("Last transaction should have TX1 its address", tx.getAddressHash(), TX1.address);
        assertEquals("Last transaction should have TX1 its timestamp", tx.getAttachmentTimestamp(), TX1.attachmentTimestamp);
        assertEquals("Last transaction should have TX1 its bytes", tx.getBytes(), TX1.bytes());
    }

}
