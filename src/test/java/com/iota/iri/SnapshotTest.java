package com.iota.iri;

import com.iota.iri.model.Hash;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Created by paul on 4/12/17.
 */
public class SnapshotTest {
    @Test
    public void getState() throws Exception {
        //Assert.assertTrue(latestSnapshot.getState().equals(Snapshot.initialState));
    }

    @Test
    public void isConsistent() throws Exception {
        Snapshot latestSnapshot = new Snapshot(Snapshot.initialSnapshot);
        Assert.assertTrue("Initial confirmed should be consistent", latestSnapshot.isConsistent());
    }
}