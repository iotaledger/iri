package com.iota.iri.network.spam;

import com.iota.iri.network.Neighbor;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DropFixedPercentageTest {

    private final List<Neighbor> neighbors = new ArrayList<>();

    @Test
    public void givenNewInstanceWhenIsSpammingThenFalse() {
        SpamPreventionStrategy strategy = new DropFixedPercentage(33, neighbors);
        assertFalse("No neighbor is spamming yet.", strategy.isSpamming(mock(Neighbor.class)));
    }

    @Test
    public void givenNoHistoryWhenIsSpammingThenFalse() {
        Neighbor a = neighbor(0, 42);
        neighbors.add(a);
        SpamPreventionStrategy strategy = new DropFixedPercentage(100, neighbors);
        assertFalse("not spamming", strategy.isSpamming(a));
    }

    @Test
    public void givenSpammingNeighborWhenIsSpammingThenTrue() {
        Neighbor a = neighbor(0, 42);
        Neighbor b = neighbor(600, 666);
        Neighbor c = neighbor(0, 3);
        neighbors.addAll(Arrays.asList(a, b, c));
        SpamPreventionStrategy strategy = new DropFixedPercentage(34, neighbors);
        strategy.calculateSpam(neighbors);
        assertFalse("not spamming", strategy.isSpamming(a));
        assertTrue("spamming", strategy.isSpamming(b));
        assertFalse("not spamming", strategy.isSpamming(c));
    }

    @Test
    public void whenCalculateSpamThenUseOnlySpecifiedNeighbors() {
        Neighbor a = neighbor(0, 42);
        Neighbor b = neighbor(0, 666);
        Neighbor c = neighbor(0, 27);
        Neighbor d = neighbor(0, 1000);
        Neighbor e = neighbor(3, 3333);
        neighbors.addAll(Arrays.asList(a, b, c, d, e));
        SpamPreventionStrategy strategy = new DropFixedPercentage(34, neighbors);
        strategy.calculateSpam(Arrays.asList(a, b, c)); // neighbors d and e are removed
        assertFalse("not spamming", strategy.isSpamming(a));
        assertTrue("spamming", strategy.isSpamming(b));
        assertFalse("not spamming", strategy.isSpamming(c));
        assertFalse("not spamming", strategy.isSpamming(d));
        assertFalse("not spamming", strategy.isSpamming(e));
    }

    @Test
    public void whenCalculateSpamThenIgnoreInactiveNeighbors() {
        Neighbor a = neighbor(0, 42);
        Neighbor b = neighbor(600, 666);
        Neighbor c = neighbor(0, 0);
        neighbors.addAll(Arrays.asList(a, b, c));
        SpamPreventionStrategy strategy = new DropFixedPercentage(34, neighbors);
        strategy.calculateSpam(neighbors);
        assertFalse("not spamming because 34% of 2 active neighbors is zero", strategy.isSpamming(b));
    }

    @Test
    public void givenSameDeltasWhenIsSpammingThenBlockRandom() {

        Neighbor a = neighbor(0, 0);
        Neighbor b = neighbor(0, 0);
        neighbors.addAll(Arrays.asList(a, b));
        SpamPreventionStrategy strategy = new DropFixedPercentage(50, neighbors);
        boolean aIsSpamming = false;
        boolean bIsSpamming = false;
        for (int i = 1; i < 25; i++) {
            when(a.getNumberOfAllTransactions()).thenReturn(i * 10L);
            when(b.getNumberOfAllTransactions()).thenReturn(i * 10L);
            System.out.println("" + i);
            strategy.calculateSpam(neighbors);
            aIsSpamming = aIsSpamming || strategy.isSpamming(a);
            bIsSpamming = bIsSpamming || strategy.isSpamming(b);
        }
        assertTrue("spamming at least once", aIsSpamming);
        assertTrue("spamming at least once", bIsSpamming);
    }

    private Neighbor neighbor(long first, long second) {
        Neighbor neighbor = mock(Neighbor.class);
        when(neighbor.getHostAddress()).thenReturn(RandomStringUtils.randomAlphabetic(10));
        when(neighbor.getNumberOfAllTransactions()).thenReturn(first).thenReturn(second);
        return neighbor;
    }

}