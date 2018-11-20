package com.iota.iri;

import com.iota.iri.conf.IotaConfig;
import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.assertFalse;

public class SnapshotTest {

    private static Snapshot initSnapshot;

    @BeforeClass
    public static void beforeClass() {
        try {
            IotaConfig config = new MainnetConfig();
            initSnapshot = Snapshot.init(config);
        } catch (IOException e) {
            throw new UncheckedIOException("Problem initiating snapshot", e);
        }
    }

    @Test
    public void getState() {
        //Assert.assertTrue(latestSnapshot.getState().equals(Snapshot.initialState));
    }

    @Test
    public void isConsistent() {
        Assert.assertTrue("Initial confirmed should be consistent", Snapshot.isConsistent(initSnapshot.state));
    }

    @Test
    public void patch() {
        Map.Entry<Hash, Long> firstOne = initSnapshot.state.entrySet().iterator().next();
        Hash someHash = HashFactory.ADDRESS.create("PSRQPWWIECDGDDZXHGJNMEVJNSVOSMECPPVRPEVRZFVIZYNNXZNTOTJOZNGCZNQVSPXBXTYUJUOXYASLS");
        Map<Hash, Long> diff = new HashMap<>();
        diff.put(firstOne.getKey(), -firstOne.getValue());
        diff.put(someHash, firstOne.getValue());
        Assert.assertNotEquals(0, diff.size());
        Assert.assertTrue("The ledger should be consistent", Snapshot.isConsistent(initSnapshot.patchedDiff(diff)));
    }

    @Test
    public void applyShouldFail() {
        Snapshot latestSnapshot = initSnapshot.clone();
        Map<Hash, Long> badMap = new HashMap<>();
        badMap.put(HashFactory.ADDRESS.create("PSRQPWWIECDGDDZEHGJNMEVJNSVOSMECPPVRPEVRZFVIZYNNXZNTOTJOZNGCZNQVSPXBXTYUJUOXYASLS"), 100L);
        badMap.put(HashFactory.ADDRESS.create("ESRQPWWIECDGDDZEHGJNMEVJNSVOSMECPPVRPEVRZFVIZYNNXZNTOTJOZNGCZNQVSPXBXTYUJUOXYASLS"), -100L);
        Map<Hash, Long> patch = latestSnapshot.patchedDiff(badMap);
        assertFalse("should be inconsistent", Snapshot.isConsistent(latestSnapshot.patchedDiff(badMap)));
    }

    private Map<Hash, Long> getModifiedMap() {
        Hash someHash = HashFactory.ADDRESS.create("PSRQPWWIECDGDDZXHGJNMEVJNSVOSMECPPVRPEVRZFVIZYNNXZNTOTJOZNGCZNQVSPXBXTYUJUOXYASLS");
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