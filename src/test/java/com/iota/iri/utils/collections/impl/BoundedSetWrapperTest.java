package com.iota.iri.utils.collections.impl;

import com.iota.iri.utils.collections.interfaces.BoundedSet;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.internal.util.collections.Sets;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class BoundedSetWrapperTest {

    @Test(expected = IllegalArgumentException.class)
    public void createSetWithException() {
        Set<Integer> set = Sets.newSet(1, 2, 3, 4, 5, 6);
        new BoundedSetWrapper<>(set, 4);
    }

    @Test
    public void createSetAndAssertEquals() {
        Set<Integer> set = Sets.newSet(1, 2, 3, 4, 5, 6);
        BoundedSet<Integer> boundedSetWrapper = new BoundedSetWrapper<>(set, 6);
        Assert.assertEquals("sets should be equal", set, boundedSetWrapper);

    }

    @Test
    public void testAdd() {
        BoundedSet<Integer> boundedSet = new BoundedSetWrapper<>(new LinkedHashSet<>(), 3);
        Assert.assertTrue("can't add", boundedSet.add(1));
        Assert.assertTrue("can't add", boundedSet.add(2));
        Assert.assertTrue("can't add", boundedSet.add(3));
        Assert.assertTrue("can't add", boundedSet.add(4));
        Assert.assertEquals("bounded set doesn't have expected contents",
                Sets.newSet(2, 3, 4), boundedSet);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddAll() {
        BoundedSet<Integer> boundedSet = new BoundedSetWrapper<>(new LinkedHashSet<>(), 3);
        boundedSet.addAll(Arrays.asList(5, 6, 7, 8, 9));
    }

}