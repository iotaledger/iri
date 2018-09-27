package com.iota.iri.controllers;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.iota.iri.model.Hash;

import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;

/**
 * Created by paul on 5/2/17.
 */
public class TipsViewModelTest {
    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void addTipHash() throws Exception {

    }

    @Test
    public void removeTipHash() throws Exception {

    }

    @Test
    public void setSolid() throws Exception {

    }

    @Test
    public void getTips() throws Exception {

    }

    @Test
    public void getRandomSolidTipHash() throws Exception {

    }

    @Test
    public void getRandomNonSolidTipHash() throws Exception {

    }

    @Test
    public void getRandomTipHash() throws Exception {

    }

    @Test
    public void nonSolidSize() throws Exception {

    }

    @Test
    public void size() throws Exception {

    }

    @Test
    public void loadTipHashes() throws Exception {

    }

    @Test
    public void nonsolidCapacityLimited() throws ExecutionException, InterruptedException {
        TipsViewModel tipsVM = new TipsViewModel();
        int capacity = TipsViewModel.MAX_TIPS;
        //fill tips list
        for (int i = 0; i < capacity * 2 ; i++) {
            Hash hash = TransactionViewModelTest.getRandomTransactionHash();
            tipsVM.addTipHash(hash);
        }
        //check that limit wasn't breached
        assertEquals(capacity, tipsVM.nonSolidSize());
    }

    @Test
    public void solidCapacityLimited() throws ExecutionException, InterruptedException {
        TipsViewModel tipsVM = new TipsViewModel();
        int capacity = TipsViewModel.MAX_TIPS;
        //fill tips list
        for (int i = 0; i < capacity * 2 ; i++) {
            Hash hash = TransactionViewModelTest.getRandomTransactionHash();
            tipsVM.addTipHash(hash);
            tipsVM.setSolid(hash);
        }
        //check that limit wasn't breached
        assertEquals(capacity, tipsVM.size());
    }

    @Test
    public void totalCapacityLimited() throws ExecutionException, InterruptedException {
        TipsViewModel tipsVM = new TipsViewModel();
        int capacity = TipsViewModel.MAX_TIPS;
        //fill tips list
        for (int i = 0; i <= capacity * 4; i++) {
            Hash hash = TransactionViewModelTest.getRandomTransactionHash();
            tipsVM.addTipHash(hash);
            if (i % 2 == 1) {
                tipsVM.setSolid(hash);
            }
        }
        //check that limit wasn't breached
        assertEquals(capacity * 2, tipsVM.size());
    }

}