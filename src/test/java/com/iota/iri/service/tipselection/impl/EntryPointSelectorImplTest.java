package com.iota.iri.service.tipselection.impl;

import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.TransactionHash;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.impl.SnapshotMockUtils;
import com.iota.iri.service.tipselection.EntryPointSelector;
import com.iota.iri.storage.Tangle;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EntryPointSelectorImplTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    
    @Mock
    private LatestMilestoneTracker latestMilestoneTracker;
    
    @Mock
    private Tangle tangle;

    @Mock
    private SnapshotProvider snapshotProvider;

    @Before
    public void setUp() throws Exception {
        MilestoneViewModel.clear();
        Mockito.when(snapshotProvider.getLatestSnapshot()).thenReturn(SnapshotMockUtils.createSnapshot());
        Mockito.when(snapshotProvider.getInitialSnapshot()).thenReturn(SnapshotMockUtils.createSnapshot());
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
