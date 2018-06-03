package com.iota.iri.service.tipselection.impl;

import com.iota.iri.Milestone;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.model.Hash;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.service.tipselection.EntryPointSelector;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EntryPointSelectorImplTest {

    @Mock
    private Milestone milestone;
    @Mock
    private Tangle tangle;

    @Test
    public void testEntryPointWithTangleData() throws Exception {
        Hash milestoneHash = Hash.calculate(SpongeFactory.Mode.CURLP81, new int[]{1});
        mockTangleBehavior(milestoneHash);
        mockMilestoneTrackerBehavior(0, Hash.NULL_HASH);

        EntryPointSelector entryPointSelector = new EntryPointSelectorImpl(tangle, milestone, false, 0);
        Hash entryPoint = entryPointSelector.getEntryPoint(10);

        Assert.assertEquals("The entry point should be the milestone in the Tangle", milestoneHash, entryPoint);
    }

    @Test
    public void testEntryPointWithoutTangleData() throws Exception {
        mockMilestoneTrackerBehavior(0, Hash.NULL_HASH);

        EntryPointSelector entryPointSelector = new EntryPointSelectorImpl(tangle, milestone, false, 0);
        Hash entryPoint = entryPointSelector.getEntryPoint(10);

        Assert.assertEquals("The entry point should be the last tracked solid milestone", Hash.NULL_HASH, entryPoint);
    }


    private void mockMilestoneTrackerBehavior(int latestSolidSubtangleMilestoneIndex, Hash latestSolidSubtangleMilestone) {
        milestone.latestSolidSubtangleMilestoneIndex = latestSolidSubtangleMilestoneIndex;
        milestone.latestSolidSubtangleMilestone = latestSolidSubtangleMilestone;
    }

    private void mockTangleBehavior(Hash milestoneModelHash) throws Exception {
        com.iota.iri.model.Milestone milestoneModel = new com.iota.iri.model.Milestone();
        milestoneModel.index = new IntegerIndex(0);
        milestoneModel.hash = milestoneModelHash;
        Pair<Indexable, Persistable> indexMilestoneModel = new Pair<>(new IntegerIndex(0), milestoneModel);
        Mockito.when(tangle.getFirst(com.iota.iri.model.Milestone.class, IntegerIndex.class))
                .thenReturn(indexMilestoneModel);
    }
}