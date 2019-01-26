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
    public void givenNewInstanceWhenIsSpammingThenFalse() {
        SpamPreventionStrategy strategy = new DropFixedPercentage(33, new ArrayList<>());
        assertFalse("No neighbor is spamming yet.", strategy.isSpamming(mock(Neighbor.class)));
    }

    @Test
    public void givenNoHistoryWhenIsSpammingThenFalse() {
        Neighbor a = neighbor(0, 42);
        SpamPreventionStrategy strategy = new DropFixedPercentage(100, Collections.singletonList(a));
        assertFalse("not spamming", strategy.isSpamming(a));
    }

    @Test
    public void givenSpammingNeighborWhenIsSpammingThenTrue() {
        Neighbor a = neighbor(0, 42);
        Neighbor b = neighbor(600, 666);
        Neighbor c = neighbor(0, 3);
        List<Neighbor> neighbors = Arrays.asList(a, b, c);
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
        Neighbor d = neighbor(0, 999);
        Neighbor e = neighbor(3, 3333);
        SpamPreventionStrategy strategy = new DropFixedPercentage(34, Arrays.asList(a, b, c, d, e));
        strategy.calculateSpam(Arrays.asList(a, b, c)); // neighbors d and e are removed
        assertFalse("not spamming", strategy.isSpamming(a));
        assertTrue("spamming", strategy.isSpamming(b));
        assertFalse("not spamming", strategy.isSpamming(c));
        assertFalse("not spamming", strategy.isSpamming(d));
        assertFalse("not spamming", strategy.isSpamming(e));
    }

    @Test
    public void givenNewNeighborsWhenCalculateSpamThenIgnoreThemOnFirstCalculation() {
        Neighbor a = neighbor();
        Neighbor b = neighbor();
        Neighbor c = neighbor();
        SpamPreventionStrategy strategy = new DropFixedPercentage(50, Arrays.asList(a, b));

        when(a.getNumberOfAllTransactions()).thenReturn(3L);
        when(b.getNumberOfAllTransactions()).thenReturn(1L);
        when(c.getNumberOfAllTransactions()).thenReturn(6L);
        strategy.calculateSpam(Arrays.asList(a, b, c)); // c is new
        assertTrue("spamming", strategy.isSpamming(a));
        assertFalse("not spamming", strategy.isSpamming(b));
        assertFalse("not spamming yet", strategy.isSpamming(c));

        when(a.getNumberOfAllTransactions()).thenReturn(30L);
        when(b.getNumberOfAllTransactions()).thenReturn(43L);
        when(c.getNumberOfAllTransactions()).thenReturn(672L);
        strategy.calculateSpam(Arrays.asList(a, b, c)); // c has largest delta now
        assertFalse("not spamming", strategy.isSpamming(a));
        assertFalse("not spamming", strategy.isSpamming(b));
        assertTrue("spamming", strategy.isSpamming(c));
    }

    @Test
    public void whenCalculateSpamThenIgnoreInactiveNeighbors() {
        Neighbor a = neighbor(0, 42);
        Neighbor b = neighbor(600, 666);
        Neighbor c = neighbor(0, 0);
        List<Neighbor> neighbors = Arrays.asList(a, b, c);
        SpamPreventionStrategy strategy = new DropFixedPercentage(34, neighbors);
        strategy.calculateSpam(neighbors);
        assertFalse("not spamming because 34% of 2 active neighbors is zero", strategy.isSpamming(b));
    }

    @Test
    public void givenSameDeltasWhenIsSpammingThenBlockRandom() {
        Neighbor a = neighbor();
        Neighbor b = neighbor();
        List<Neighbor> neighbors = Arrays.asList(a, b);
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
        Neighbor neighbor = neighbor();
        when(neighbor.getNumberOfAllTransactions()).thenReturn(first).thenReturn(second);
        return neighbor;
    }

    private Neighbor neighbor() {
        Neighbor neighbor = mock(Neighbor.class);
        when(neighbor.getHostAddress()).thenReturn(RandomStringUtils.randomAlphabetic(10));
        return neighbor;
    }

}