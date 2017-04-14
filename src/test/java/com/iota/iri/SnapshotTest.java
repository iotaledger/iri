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
        Assert.assertTrue(Snapshot.latestSnapshot.getState().equals(Snapshot.initialState));
    }

    @Test
    public void isConsistent() throws Exception {
        Assert.assertTrue("Initial snapshot should be consistent", Snapshot.latestSnapshot.isConsistent());
    }

    @Test
    public void diff() throws Exception {
        Assert.assertEquals(Snapshot.latestSnapshot.diff(getModifiedMap(Snapshot.latestSnapshot)).keySet().size(), 2);
    }

    @Test
    public void patch() throws Exception {
        Map<Hash, Long> diff = Snapshot.latestSnapshot.diff(getModifiedMap(Snapshot.latestSnapshot));
        Snapshot newState = Snapshot.latestSnapshot.patch(diff);
        diff = Snapshot.latestSnapshot.diff(newState.getState());
        Assert.assertNotEquals(0, diff.size());
        Assert.assertTrue("The ledger should be consistent", newState.isConsistent());
    }

    @Test
    public void merge() throws Exception {
        Snapshot latestCopy = Snapshot.latestSnapshot.patch(Snapshot.latestSnapshot.diff(Snapshot.latestSnapshot.getState()));
        Snapshot patch = latestCopy.patch(latestCopy.diff(getModifiedMap(latestCopy)));
        latestCopy.merge(patch);
        assertNotEquals("State should be changed.", latestCopy.getState(), Snapshot.initialState);
    }

    private Map<Hash, Long> getModifiedMap(Snapshot fromSnapshot) {
        Hash someHash = new Hash("PSRQPWWIECDGDDZXHGJNMEVJNSVOSMECPPVRPEVRZFVIZYNNXZNTOTJOZNGCZNQVSPXBXTYUJUOXYASLS");
        Map<Hash, Long> newMap;
        newMap = (Map<Hash, Long>) new HashMap<>(fromSnapshot.getState()).clone();
        Iterator<Map.Entry<Hash, Long>> iterator = newMap.entrySet().iterator();
        Map.Entry<Hash, Long> entry;
        if(iterator.hasNext()) {
            entry = iterator.next();
            Long value = entry.getValue();
            Hash hash = entry.getKey();
            newMap.put(hash, 0L);
            newMap.put(someHash, value);
        }
        return newMap;
    }
}