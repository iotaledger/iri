package com.iota.iri;

import com.iota.iri.conf.Configuration;
import com.iota.iri.model.Hash;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;

public class SnapshotTest {

    private static Snapshot initSnapshot;

    @BeforeClass
    public static void beforeClass() {
        try {
            initSnapshot = Snapshot.init(Configuration.MAINNET_SNAPSHOT_FILE,
                Configuration.MAINNET_SNAPSHOT_SIG_FILE, false);
        } catch (IOException e) {
            throw new UncheckedIOException("Problem initiating snapshot", e);
        }
    }

    @Test
    public void getState() {
        Map<Hash, Long> copyState = initSnapshot.copySnapshot().copyState();
        Assert.assertEquals(initSnapshot.copyState(), copyState);
    }

    @Test
    public void isConsistent() {
        Assert.assertTrue("Initial confirmed should be consistent", initSnapshot.isConsistent());
        Assert.assertTrue("Initial confirmed should be consistent", Snapshot.isConsistent(initSnapshot.copyState()));
    }

    @Test
    public void patch() {
        Map.Entry<Hash, Long> firstOne = initSnapshot.copyState().entrySet().iterator().next();
        Hash someHash = new Hash("PSRQPWWIECDGDDZXHGJNMEVJNSVOSMECPPVRPEVRZFVIZYNNXZNTOTJOZNGCZNQVSPXBXTYUJUOXYASLS");
        Map<Hash, Long> diff = new HashMap<>();
        diff.put(firstOne.getKey(), -firstOne.getValue());
        diff.put(someHash, firstOne.getValue());
        Assert.assertNotEquals(0, diff.size());
        Assert.assertTrue("The ledger should be consistent", Snapshot.isConsistent(initSnapshot.patchedDiff(diff)));
    }

    @Test
    public void applyShouldFail() {
        Snapshot latestSnapshot = initSnapshot.copySnapshot();
        Map<Hash, Long> badMap = new HashMap<>();
        badMap.put(new Hash("PSRQPWWIECDGDDZEHGJNMEVJNSVOSMECPPVRPEVRZFVIZYNNXZNTOTJOZNGCZNQVSPXBXTYUJUOXYASLS"), 100L);
        badMap.put(new Hash("ESRQPWWIECDGDDZEHGJNMEVJNSVOSMECPPVRPEVRZFVIZYNNXZNTOTJOZNGCZNQVSPXBXTYUJUOXYASLS"), -100L);
        Map<Hash, Long> patch = latestSnapshot.patchedDiff(badMap);
        assertFalse("should be inconsistent", Snapshot.isConsistent(patch));
    }
}