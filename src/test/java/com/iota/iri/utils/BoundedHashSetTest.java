package com.iota.iri.utils;

import com.iota.iri.utils.collections.impl.BoundedHashSet;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class BoundedHashSetTest {

    @Test
    public void createBoundedHashSetWithCollectionTest() {
        List<Integer> list = Arrays.asList(1, 2 ,3, 4, 5, 6);
        BoundedHashSet<Integer> boundedSet = new BoundedHashSet<>(list, 4);
        Assert.assertEquals(new HashSet<>(Arrays.asList(1, 2, 3, 4)), boundedSet);
    }

    @Test
    public void testAdd() {
        BoundedHashSet<Integer> boundedSet = new BoundedHashSet<>(3);
        Assert.assertTrue("can't add to unfull set", boundedSet.add(1));
        Assert.assertTrue("can't add to unfull set", boundedSet.add(2));
        Assert.assertTrue("can't add to unfull set", boundedSet.add(3));
        Assert.assertFalse("can add to full set", boundedSet.add(4));
        Assert.assertEquals("bounded set doesn't have expected contents",
                new HashSet<>(Arrays.asList(1, 2, 3)), boundedSet);
    }

    @Test
    public void testAddAll() {
        BoundedHashSet<Integer> boundedSet = new BoundedHashSet<>(3);
        Assert.assertTrue("set did not change after add", boundedSet.addAll(Arrays.asList(5, 6, 7, 8, 9)));
        Assert.assertEquals("bounded set doesn't have expected contents",
                new HashSet<>(Arrays.asList(5, 6, 7)), boundedSet);
    }

}
