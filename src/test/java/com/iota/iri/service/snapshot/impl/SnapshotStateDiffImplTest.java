package com.iota.iri.service.snapshot.impl;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.model.Hash;

public class SnapshotStateDiffImplTest {
    
    private static final Hash A = TransactionTestUtils.getTransactionHash();
    private static final Hash B = TransactionTestUtils.getTransactionHash();
    private static final Hash C = TransactionTestUtils.getTransactionHash();
    
    @Test
    public void getBalanceChanges() {
        SnapshotStateDiffImpl stateDiff = new SnapshotStateDiffImpl(Collections.EMPTY_MAP);
        Map<Hash, Long> change = stateDiff.getBalanceChanges();
        change.put(A, 1l);
        
        assertNotEquals("Changes to the statediff balance changes shouldnt reflect on the original state", 
                stateDiff.getBalanceChanges().size(), change.size());
    }

    @Test
    public void isConsistent() {
        SnapshotStateDiffImpl stateDiff = new SnapshotStateDiffImpl(new HashMap<Hash, Long>(){{
            put(A, 1l);
            put(B, 5l);
            put(C, -6l);
        }});
        assertTrue("Sum of diffs should be 0", stateDiff.isConsistent());
        
        stateDiff = new SnapshotStateDiffImpl(Collections.EMPTY_MAP);
        assertTrue("Empty diff should be consisntent as sum is 0", stateDiff.isConsistent());
        
        stateDiff = new SnapshotStateDiffImpl(new HashMap<Hash, Long>(){{
            put(A, 1l);
            put(B, 5l);
        }});
        
        assertFalse("Diff sum not 0 shouldnt be consistent", stateDiff.isConsistent());
    }
}
