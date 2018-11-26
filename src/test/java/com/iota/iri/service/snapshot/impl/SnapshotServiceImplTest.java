package com.iota.iri.service.snapshot.impl;

import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.StateDiff;
import com.iota.iri.model.persistables.Milestone;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.SnapshotState;
import com.iota.iri.storage.Tangle;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SnapshotServiceImplTest {
    //region [CONSTANTS FOR THE TEST] //////////////////////////////////////////////////////////////////////////////////

    private static Hash ADDRESS_0 = Hash.NULL_HASH;

    private static Hash ADDRESS_1 = HashFactory.ADDRESS.create(
            "EKRQUHQRZWDGFTRFSTSPAZYBXMEYGHOFIVXDCRRTXUJ9HXOAYLKFEBEZPWEPTG9ZFTOHGCQZCHIKKQ9RD");

    private static Hash ADDRESS_2 = HashFactory.ADDRESS.create(
            "GRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB");

    private static Hash ADDRESS_3 = HashFactory.ADDRESS.create(
            "JLDULQUXBL99AGZZKXMACLJRAYDUTBTMFGLEHVTLDTHVUIBYV9ZKGHLWCVFJVIYGHNXNTQUYQTISHDUSW");

    private static Hash MILESTONE_1 = HashFactory.TRANSACTION.create(
            "HZYNIHQQTMOPVVSIHGWENIVLJNIODSQBFEW9WUUFIP9BIFBXVLVGZLIZMQBEFHOOZBPVQJLKLGWVA9999");

    private static Hash MILESTONE_2 = HashFactory.TRANSACTION.create(
            "SVARYFXHBTZQYGEEUKOOOOE9IRNNUMKJPLLCBSJBCCXNRG9WKKUPQQQLKYWWBOQTAJYNZMI9AY9RZ9999");

    private static Hash MILESTONE_3 = HashFactory.TRANSACTION.create(
            "DLWUKXEELCZAMXCXAVOCJEHWGE9REAXFZWOSOTDZLCXLRMFIWLIRVOVLOIWUJVKUQVATGWKIQGYPA9999");

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region [BOILERPLATE] /////////////////////////////////////////////////////////////////////////////////////////////

    private static SnapshotServiceImpl snapshotService;
    private static SnapshotProvider snapshotProvider;
    private static Tangle tangle;

    @BeforeClass
    public static void setupClass() {
        MainnetConfig config = new MainnetConfig();

        tangle = Mockito.mock(Tangle.class);
        snapshotProvider = Mockito.mock(SnapshotProvider.class);

        snapshotService = new SnapshotServiceImpl().init(tangle, snapshotProvider, config);

        mockSnapshotProvider();
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region [TESTS] ///////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void replayMilestones() throws Exception {
        int milestoneStartIndex = snapshotProvider.getInitialSnapshot()
                .getIndex();
        Hash milestoneStartHash = snapshotProvider.getInitialSnapshot().getHash();
        Long milestoneStartTimestamp = snapshotProvider.getInitialSnapshot().getTimestamp();
        Snapshot latestSnapshot = snapshotProvider.getLatestSnapshot();

        // create consistent milestone #1
        mockMilestone(MILESTONE_1, milestoneStartIndex + 1, createMap(
                ADDRESS_0, -2337L,
                ADDRESS_1, 1337L,
                ADDRESS_2, 1000L
        ));

        // create inconsistent milestone #2
        mockMilestone(MILESTONE_3, milestoneStartIndex + 5, createMap(
                ADDRESS_0, -1234L,
                ADDRESS_2, 1000L,
                ADDRESS_3, 1234L
        ));

        // replaying the inconsistent milestones
        try {
            snapshotService.replayMilestones(latestSnapshot, milestoneStartIndex + 7);

            Assert.fail("replaying inconsistent milestones should raise an exception");
        } catch (SnapshotException e) {
            // a failed replay should not modify the snapshot
            Assert.assertEquals(latestSnapshot.getIndex(), milestoneStartIndex);
            Assert.assertEquals(latestSnapshot.getHash(), milestoneStartHash);
            Assert.assertEquals(latestSnapshot.getTimestamp(), (long) milestoneStartTimestamp);
            Assert.assertNull(latestSnapshot.getBalance(ADDRESS_1));
            Assert.assertNull(latestSnapshot.getBalance(ADDRESS_2));
            Assert.assertNull(latestSnapshot.getBalance(ADDRESS_3));
        }

        // overwrite the inconsistent milestone #2 with a consistent one
        mockMilestone(MILESTONE_3, milestoneStartIndex + 5, createMap(
                ADDRESS_0, -1234L,
                ADDRESS_2, 1000L,
                ADDRESS_3, 234L
        ));

        // replay the consistent milestones
        snapshotService.replayMilestones(latestSnapshot, milestoneStartIndex + 7);

        // check if all changes were applied correctly
        Assert.assertEquals(latestSnapshot.getIndex(), milestoneStartIndex + 5);
        Assert.assertEquals(latestSnapshot.getHash(), MILESTONE_3);
        Assert.assertEquals(1337L, (long) latestSnapshot.getBalance(ADDRESS_1));
        Assert.assertEquals(2000L, (long) latestSnapshot.getBalance(ADDRESS_2));
        Assert.assertEquals(234L, (long) latestSnapshot.getBalance(ADDRESS_3));
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region [UTILITY METHODS] /////////////////////////////////////////////////////////////////////////////////////////

    private static void mockSnapshotProvider() {
        Snapshot initialSnapshot = new SnapshotImpl(
                new SnapshotStateImpl(createMap(
                        ADDRESS_0, 10000L
                )),
                new SnapshotMetaDataImpl(MILESTONE_1, 0, 1l, new HashMap<>(), new HashMap<>())
        );
        Snapshot latestSnapshot = new SnapshotImpl(
                new SnapshotStateImpl(createMap(
                        ADDRESS_0, 10000L
                )),
                new SnapshotMetaDataImpl(MILESTONE_1, 0, 1l, new HashMap<>(), new HashMap<>())
        );

        Mockito.when(snapshotProvider.getInitialSnapshot()).thenReturn(initialSnapshot);
        Mockito.when(snapshotProvider.getLatestSnapshot()).thenReturn(latestSnapshot);
    }

    private void mockMilestone(Hash hash, int index, Map<Hash, Long> balanceChanges) throws Exception {
        mockMilestone(hash, index);
        mockStateDiff(hash, balanceChanges);
    }

    private void mockMilestone(Hash hash, int index) throws Exception {
        Milestone mockedMilestone = new Milestone();

        mockedMilestone.index = new IntegerIndex(index);
        mockedMilestone.hash = hash;

        Mockito.when(tangle.load(Milestone.class, mockedMilestone.index)).thenReturn(mockedMilestone);
    }

    private void mockStateDiff(Hash milestoneTransactionHash, Map<Hash, Long> diff) throws Exception {
        StateDiff mockedStateDiff = new StateDiff();

        mockedStateDiff.state = diff;

        Mockito.when(tangle.load(StateDiff.class, milestoneTransactionHash)).thenReturn(mockedStateDiff);
    }

    private static <KEY, VALUE> Map<KEY, VALUE> createMap(Object... mapEntries) {
        Map<KEY, VALUE> result = new HashMap<>();

        for (int i = 0; i < mapEntries.length / 2; i++) {
            result.put((KEY) mapEntries[i * 2], (VALUE) mapEntries[i * 2 + 1]);
        }

        return result;
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////
}
