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
        Assert.assertTrue("Initial confirmed should be consistent", Snapshot.isConsistent(Snapshot.initialState));
    }

    @Test
    public void patch() throws Exception {
        Snapshot latestSnapshot = Snapshot.initialSnapshot.clone();
        Map<Hash, Long> diff = getModifiedMap();
        Assert.assertNotEquals(0, diff.size());
        Assert.assertTrue("The ledger should be consistent", Snapshot.isConsistent(latestSnapshot.patch(diff)));
    }

    @Test
    public void merge() throws Exception {
        Snapshot latestSnapshot = Snapshot.initialSnapshot.clone();
        Map<Hash, Long> patch = latestSnapshot.patch(getModifiedMap());
        Map<Hash, Long> badMap = new HashMap<>();
        badMap.put(new Hash("PSRQPWWIECDGDDZEHGJNMEVJNSVOSMECPPVRPEVRZFVIZYNNXZNTOTJOZNGCZNQVSPXBXTYUJUOXYASLS"), 100L);
        assertFalse("should be inconsistent", Snapshot.isConsistent(latestSnapshot.patch(badMap)));
    }

    private Map<Hash, Long> getModifiedMap() {
        Hash someHash = new Hash("PSRQPWWIECDGDDZXHGJNMEVJNSVOSMECPPVRPEVRZFVIZYNNXZNTOTJOZNGCZNQVSPXBXTYUJUOXYASLS");
        Map<Hash, Long> newMap;
        newMap = new HashMap<>();
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