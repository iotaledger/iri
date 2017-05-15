package com.iota.iri;

import com.iota.iri.model.Hash;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.iota.iri.Snapshot.latestSnapshot;
import static org.junit.Assert.*;

/**
 * Created by paul on 4/12/17.
 */
public class SnapshotTest {
    @Test
    public void getState() throws Exception {
        Assert.assertTrue(latestSnapshot.getState().equals(Snapshot.initialState));
    }

    @Test
    public void isConsistent() throws Exception {
        Assert.assertTrue("Initial confirmed should be consistent", latestSnapshot.isConsistent());
    }

    @Test
    public void diff() throws Exception {
        Assert.assertEquals(latestSnapshot.diff(getModifiedMap(latestSnapshot)).keySet().size(), 2);
    }

    @Test
    public void patch() throws Exception {
        Map<Hash, Long> diff = latestSnapshot.diff(getModifiedMap(latestSnapshot));
        Snapshot newState = latestSnapshot.patch(diff, 0);
        diff = latestSnapshot.diff(newState.getState());
        Assert.assertNotEquals(0, diff.size());
        Assert.assertTrue("The ledger should be consistent", newState.isConsistent());
    }

    @Test
    public void merge() throws Exception {
        Snapshot latestCopy = latestSnapshot.patch(latestSnapshot.diff(latestSnapshot.getState()), 0);
        Snapshot patch = latestCopy.patch(latestCopy.diff(getModifiedMap(latestCopy)), 0);
        latestCopy.merge(patch);
        assertNotEquals("State should be unchanged.", latestCopy.getState(), Snapshot.initialState);

        latestCopy = latestSnapshot.patch(latestSnapshot.diff(latestSnapshot.getState()), 0);
        Map<Hash, Long> badMap = new HashMap<>();
        badMap.put(new Hash("PSRQPWWIECDGDDZEHGJNMEVJNSVOSMECPPVRPEVRZFVIZYNNXZNTOTJOZNGCZNQVSPXBXTYUJUOXYASLS"), 100L);
        latestCopy.merge(latestCopy.patch(badMap, 0));
        assertFalse("should be inconsistent", latestCopy.isConsistent());
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