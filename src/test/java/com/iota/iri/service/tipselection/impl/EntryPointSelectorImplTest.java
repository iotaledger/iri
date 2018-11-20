package com.iota.iri.service.tipselection.impl;

import com.iota.iri.MilestoneTracker;
import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.impl.SnapshotProviderImpl;
import com.iota.iri.service.tipselection.EntryPointSelector;
import com.iota.iri.storage.Tangle;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EntryPointSelectorImplTest {

    @Mock
    private LatestMilestoneTracker latestMilestoneTracker;
    @Mock
    private Tangle tangle;
    private static SnapshotProvider snapshotProvider;

    @BeforeClass
    public static void setUp() throws Exception {
        snapshotProvider = new SnapshotProviderImpl().init(new MainnetConfig());
        MilestoneViewModel.clear();
    }

    @Test
    public void testEntryPointBWithTangleData() throws Exception {
        Hash milestoneHash = TransactionHash.calculate(SpongeFactory.Mode.CURLP81, new byte[]{1});
        mockTangleBehavior(milestoneHash);
        mockMilestoneTrackerBehavior(snapshotProvider.getInitialSnapshot().getIndex() + 1, Hash.NULL_HASH);

        EntryPointSelector entryPointSelector = new EntryPointSelectorImpl(tangle, snapshotProvider, latestMilestoneTracker);
        Hash entryPoint = entryPointSelector.getEntryPoint(10);

        Assert.assertEquals("The entry point should be the milestone in the Tangle", milestoneHash, entryPoint);
    }

    @Test
    public void testEntryPointAWithoutTangleData() throws Exception {
        mockMilestoneTrackerBehavior(0, Hash.NULL_HASH);

        EntryPointSelector entryPointSelector = new EntryPointSelectorImpl(tangle, snapshotProvider, latestMilestoneTracker);
        Hash entryPoint = entryPointSelector.getEntryPoint(10);

        Assert.assertEquals("The entry point should be the last tracked solid milestone", Hash.NULL_HASH, entryPoint);
    }


    private void mockMilestoneTrackerBehavior(int latestSolidSubtangleMilestoneIndex, Hash latestSolidSubtangleMilestone) {
        snapshotProvider.getLatestSnapshot().setIndex(latestSolidSubtangleMilestoneIndex);
        snapshotProvider.getLatestSnapshot().setHash(latestSolidSubtangleMilestone);
        Mockito.when(latestMilestoneTracker.getLatestMilestoneIndex()).thenReturn(latestSolidSubtangleMilestoneIndex);
    }

    private void mockTangleBehavior(Hash milestoneModelHash) throws Exception {
        com.iota.iri.model.persistables.Milestone milestoneModel = new com.iota.iri.model.persistables.Milestone();
        milestoneModel.index = new IntegerIndex(snapshotProvider.getInitialSnapshot().getIndex() + 1);
        milestoneModel.hash = milestoneModelHash;
        Mockito.when(tangle.load(com.iota.iri.model.persistables.Milestone.class, milestoneModel.index))
                .thenReturn(milestoneModel);
    }
}
