package com.iota.iri.network.spam;

import com.iota.iri.network.Neighbor;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DropFixedPercentageTest {

    @Test
    public void givenNewInstanceWhenCheckBroadcastThenBlockNone() {
        SpamPreventionStrategy strategy = new DropFixedPercentage(33, new ArrayList<>());
        assertTrue("Broadcast: no neighbor is spamming yet.", strategy.broadcastTransactionFrom(mock(Neighbor.class)));
    }

    @Test
    public void givenOneNeighborWhenCheckBroadcastThenBlockNone() {
        Neighbor a = neighbor(0, 42);
        List<Neighbor> neighbors = Collections.singletonList(a);
        SpamPreventionStrategy strategy = new DropFixedPercentage(33, neighbors);
        strategy.calculateSpam(neighbors);
        assertTrue("Broadcast: a is not spamming.", strategy.broadcastTransactionFrom(a));
    }

    @Test
    public void givenTwoNeighborsWhenCheckBroadcastThenBlockOne() {
        Neighbor a = neighbor(0, 42);
        Neighbor b = neighbor(0, 666);
        List<Neighbor> neighbors = Arrays.asList(a, b);
        SpamPreventionStrategy strategy = new DropFixedPercentage(33, neighbors);
        strategy.calculateSpam(neighbors);
        assertTrue("Broadcast: a is not spamming.", strategy.broadcastTransactionFrom(a));
        assertFalse("Do not broadcast: b is spamming.", strategy.broadcastTransactionFrom(b));
    }

    @Test
    public void givenThreeNeighborsWhenCheckBroadcastThenBlockOne() {
        Neighbor a = neighbor(10, 42);
        Neighbor b = neighbor(10, 666);
        Neighbor c = neighbor(10, 10);
        List<Neighbor> neighbors = Arrays.asList(a, b, c);
        SpamPreventionStrategy strategy = new DropFixedPercentage(33, neighbors);
        strategy.calculateSpam(neighbors);
        assertTrue("Broadcast: a is not spamming.", strategy.broadcastTransactionFrom(a));
        assertFalse("Do not broadcast: b is spamming.", strategy.broadcastTransactionFrom(b));
        assertTrue("Broadcast: c is not spamming.", strategy.broadcastTransactionFrom(c));
    }

    @Test
    public void givenFourNeighborsWhenCheckBroadcastThenBlockTwo() {
        Neighbor a = neighbor(0, 42);
        Neighbor b = neighbor(0, 666);
        Neighbor c = neighbor(0, 10);
        Neighbor d = neighbor(0, 777);
        List<Neighbor> neighbors = Arrays.asList(a, b, c, d);
        SpamPreventionStrategy strategy = new DropFixedPercentage(33, neighbors);
        strategy.calculateSpam(neighbors);
        assertTrue("Broadcast: a is not spamming.", strategy.broadcastTransactionFrom(a));
        assertFalse("Do not broadcast: b is spamming.", strategy.broadcastTransactionFrom(b));
        assertTrue("Broadcast: c is not spamming.", strategy.broadcastTransactionFrom(c));
        assertFalse("Do not broadcast: d is spamming.", strategy.broadcastTransactionFrom(d));
    }

    @Test
    public void whenCalculateSpamThenIgnoreInactiveNeighbors() {
        Neighbor a = neighbor(0, 42);
        Neighbor b = neighbor(0, 666);
        Neighbor c = neighbor(0, 0); // inactive
        Neighbor d = neighbor(0, 0); // inactive
        List<Neighbor> neighbors = Arrays.asList(a, b, c, d);
        SpamPreventionStrategy strategy = new DropFixedPercentage(33, neighbors);
        strategy.calculateSpam(neighbors); // one out of two active should be spamming
        assertTrue("Broadcast: a is not spamming.", strategy.broadcastTransactionFrom(a));
        assertFalse("Do not broadcast: b is spamming.", strategy.broadcastTransactionFrom(b));
        assertTrue("Broadcast: c is not spamming.", strategy.broadcastTransactionFrom(c));
        assertTrue("Broadcast: d is not spamming.", strategy.broadcastTransactionFrom(d));
    }

    @Test
    public void givenRemovedNeighborWhenCalculateSpamThenIgnore() {
        Neighbor a = neighbor(0, 42);
        Neighbor b = neighbor(0, 666);
        Neighbor c = neighbor(0, 27);
        Neighbor d = neighbor(0, 999);
        SpamPreventionStrategy strategy = new DropFixedPercentage(33, Arrays.asList(a, b, c, d));
        strategy.calculateSpam(Arrays.asList(a, b, c)); // neighbor d removed
        assertTrue("Broadcast: a is not spamming", strategy.broadcastTransactionFrom(a));
        assertFalse("Do not broadcast: b is spamming.", strategy.broadcastTransactionFrom(b));
        assertTrue("Broadcast: c is not spamming", strategy.broadcastTransactionFrom(c));
        assertTrue("Broadcast: d has not been considered in the calculation.", strategy.broadcastTransactionFrom(d));
    }

    @Test
    public void givenNewNeighborWhenCalculateSpamThenIgnore() {
        Neighbor a = neighbor();
        Neighbor b = neighbor();
        Neighbor c = neighbor();
        SpamPreventionStrategy strategy = new DropFixedPercentage(33, Arrays.asList(a, b));

        when(a.getNumberOfNewTransactions()).thenReturn(3L);
        when(b.getNumberOfNewTransactions()).thenReturn(1L);
        when(c.getNumberOfNewTransactions()).thenReturn(6L);
        strategy.calculateSpam(Arrays.asList(a, b, c)); // c is new
        assertFalse("Do not broadcast: a is spamming.", strategy.broadcastTransactionFrom(a));
        assertTrue("Broadcast: b is not spamming.", strategy.broadcastTransactionFrom(b));
        assertTrue("Broadcast: c is a new neighbor.", strategy.broadcastTransactionFrom(c));
    }

    @Test
    public void givenSameDeltasWhenCalculateSpamThenChooseRandomNeighbor() {
        Neighbor a = neighbor();
        Neighbor b = neighbor();
        Neighbor c = neighbor();
        List<Neighbor> neighbors = Arrays.asList(a, b, c);
        SpamPreventionStrategy strategy = new DropFixedPercentage(33, neighbors);
        boolean aSpamming = false;
        boolean bSpamming = false;
        boolean cSpamming = false;
        for (int i = 1; i < 100; i++) {
            when(a.getNumberOfNewTransactions()).thenReturn(i * 10L);
            when(b.getNumberOfNewTransactions()).thenReturn(i * 10L);
            when(c.getNumberOfNewTransactions()).thenReturn(i * 10L);
            strategy.calculateSpam(neighbors);
            aSpamming = aSpamming || !strategy.broadcastTransactionFrom(a);
            bSpamming = bSpamming || !strategy.broadcastTransactionFrom(b);
            cSpamming = cSpamming || !strategy.broadcastTransactionFrom(c);
        }
        assertTrue("a has been spamming at least once", aSpamming);
        assertTrue("b has been spamming at least once", bSpamming);
        assertTrue("c has been spamming at least once", cSpamming);
    }

    private Neighbor neighbor(long first, long second) {
        Neighbor neighbor = neighbor();
        when(neighbor.getNumberOfNewTransactions()).thenReturn(first).thenReturn(second);
        return neighbor;
    }

    private Neighbor neighbor() {
        Neighbor neighbor = mock(Neighbor.class);
        when(neighbor.getHostAddress()).thenReturn(RandomStringUtils.randomAlphabetic(10));
        return neighbor;
    }

}