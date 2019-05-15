package com.iota.iri.service.snapshot.impl;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.iota.iri.TransactionTestUtils;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotState;
import com.iota.iri.service.snapshot.SnapshotStateDiff;

public class SnapshotStateImplTest {
    
    private static final Hash A = TransactionTestUtils.getTransactionHash();
    private static final Hash B = TransactionTestUtils.getTransactionHash();
    
    private static Map<Hash, Long> map = new HashMap<Hash, Long>(){{
        put(Hash.NULL_HASH, TransactionViewModel.SUPPLY - 10);
        put(A, 10l);
    }};
    
    private static Map<Hash, Long> inconsistentMap = new HashMap<Hash, Long>(){{
        put(Hash.NULL_HASH, 5l);
        put(A, -10l);
    }};

    private SnapshotStateImpl state;
    private SnapshotStateImpl balanceState;

    @Before
    public void setUp() throws Exception {
        state = new SnapshotStateImpl(new HashMap<>());
        balanceState = new SnapshotStateImpl(map);
    }

    @Test
    public void testGetBalance() {
        assertNull("Unknown address should return null", balanceState.getBalance(null));
        
        long balance = balanceState.getBalance(Hash.NULL_HASH);
        assertEquals("Balance should be total - 10", TransactionViewModel.SUPPLY - 10l, balance);
        
        balance = balanceState.getBalance(A);
        assertEquals("Balance should be 10", 10l, balance);
    }

    @Test
    public void testGetBalances() {
        assertEquals("State should not have balances", new HashMap<>(), state.getBalances());
        assertEquals("State should have the balances it was created with", map, balanceState.getBalances());
    }

    @Test
    public void testIsConsistent() {
        assertTrue("Empty balance should be consistent", state.isConsistent());
        assertTrue("No negative balances should be consistent", balanceState.isConsistent());
        
        SnapshotStateImpl inconsistentState = new SnapshotStateImpl(inconsistentMap);
        assertFalse("Negative balances should not be consistent", inconsistentState.isConsistent());
    }

    @Test
    public void testHasCorrectSupply() {
        assertFalse("Empty state should not have correct supply", state.hasCorrectSupply());
        assertTrue("State with total supply should have correct supply", balanceState.hasCorrectSupply());
        
        SnapshotStateImpl inconsistentState = new SnapshotStateImpl(inconsistentMap);
        assertFalse("Inconsistent state without full supply should be incorrect", inconsistentState.hasCorrectSupply());
        
        Map<Hash, Long> map = new HashMap<>();
        map.put(Hash.NULL_HASH, TransactionViewModel.SUPPLY - 10);
        map.put(A, -10l);
        map.put(B,  20l);
        assertFalse("Inconsistent state with full supply should be correct", inconsistentState.hasCorrectSupply());
    }

    @Test
    public void testUpdate() {
        assertNotEquals("States with different balances should not be equal", state, balanceState);
        state.update(balanceState);
        
        assertEquals("Updating a state with another state should make them equal", state, balanceState);
    }

    @Test
    public void testApplyStateDiff() throws SnapshotException {
        Map<Hash, Long> map = new HashMap<>();
        map.put(Hash.NULL_HASH, 5l);
        map.put(A, -5l);
        
        SnapshotStateDiff diff = new SnapshotStateDiffImpl(map);
        state.applyStateDiff(diff);
        
        long balance = state.getBalance(Hash.NULL_HASH);
        assertEquals("Applying state to an empty state should have 5 for genesis", 5l, balance);
        
        balance = state.getBalance(A);
        assertEquals("Applying state to an empty state should have -5 for A", -5l, balance);
    }
    
    @Test(expected = SnapshotException.class)
    public void testApplyStateDiffThrowsException() throws SnapshotException {
        SnapshotStateDiff diff = new SnapshotStateDiffImpl(inconsistentMap);
        state.applyStateDiff(diff);
        
        fail("Applying an inconsistent state should throw an exception");
    }

    @Test
    public void testPatchedState() {
        SnapshotStateDiff diff = new SnapshotStateDiffImpl(map);
        SnapshotState patchedState = state.patchedState(diff);
        
        assertEquals("Patching an empty state with a map should equal to creation with that map", patchedState, balanceState);
        
        Map<Hash, Long> map = new HashMap<>();
        map.put(Hash.NULL_HASH, 5l);
        map.put(A, -5l);
        
        diff = new SnapshotStateDiffImpl(map);
        patchedState = balanceState.patchedState(diff);
        
        long balance = patchedState.getBalance(Hash.NULL_HASH);
        assertEquals("5 should have been added to genesis", TransactionViewModel.SUPPLY - 5l, balance);
        
        balance = patchedState.getBalance(A);
        assertEquals("5 should have been removed from A", 5, balance);
    }
}
