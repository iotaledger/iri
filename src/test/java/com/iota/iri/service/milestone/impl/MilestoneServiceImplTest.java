package com.iota.iri.service.milestone.impl;

import com.iota.iri.TangleMockUtils;
import com.iota.iri.conf.ConsensusConfig;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.service.milestone.MilestoneException;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.SnapshotService;
import com.iota.iri.service.snapshot.impl.SnapshotMockUtils;
import com.iota.iri.storage.Tangle;
import com.iota.iri.zmq.MessageQ;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MilestoneServiceImplTest {
    //region [BOILERPLATE] /////////////////////////////////////////////////////////////////////////////////////////////

    @Mock
    private Tangle tangle;

    @Mock
    private SnapshotProvider snapshotProvider;

    @Mock
    private SnapshotService snapshotService;

    @Mock
    private MessageQ messageQ;

    @Mock
    private ConsensusConfig config;

    @InjectMocks
    private MilestoneServiceImpl milestoneService;

    @Before
    public void setup() {
        SnapshotMockUtils.mockSnapshotProvider(snapshotProvider);

        MilestoneViewModel.clear();
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region [findLatestProcessedSolidMilestoneInDatabase] /////////////////////////////////////////////////////////////

    private enum MockedMilestone {
        MILESTONE_1("ARWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 70001),
        MILESTONE_2("BRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 70002),
        MILESTONE_3("CRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 70003),
        MILESTONE_4("JRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 70010),
        MILESTONE_5("KRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 70011),
        MILESTONE_6("LRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 70012);

        private final Hash transactionHash;

        private final int milestoneIndex;

        MockedMilestone(String transactionHash, int milestoneIndex) {
            this.transactionHash = HashFactory.TRANSACTION.create(transactionHash);
            this.milestoneIndex = milestoneIndex;
        }

        public void mockProcessed(Tangle tangle, boolean applied) {
            TangleMockUtils.mockMilestone(tangle, transactionHash, milestoneIndex, applied);
        }
    }

    @Test
    public void findLatestProcessedSolidMilestoneInDatabaseNone() throws Exception {
        MockedMilestone.MILESTONE_1.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_2.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_3.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_4.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_5.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_6.mockProcessed(tangle, false);

        assertLatestProcessedSolidMilestoneEquals(null);
    }

    @Test
    public void findLatestProcessedSolidMilestoneInDatabaseAtEnd() throws Exception {
        MockedMilestone.MILESTONE_1.mockProcessed(tangle, true);
        MockedMilestone.MILESTONE_2.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_3.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_4.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_5.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_6.mockProcessed(tangle, true);

        assertLatestProcessedSolidMilestoneEquals(MockedMilestone.MILESTONE_6);
    }

    @Test
    public void findLatestProcessedSolidMilestoneInDatabaseNearEnd() throws Exception {
        MockedMilestone.MILESTONE_1.mockProcessed(tangle, true);
        MockedMilestone.MILESTONE_2.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_3.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_4.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_5.mockProcessed(tangle, true);
        MockedMilestone.MILESTONE_6.mockProcessed(tangle, false);

        assertLatestProcessedSolidMilestoneEquals(MockedMilestone.MILESTONE_5);
    }

    @Test
    public void findLatestProcessedSolidMilestoneInDatabaseAtStart() throws Exception {
        MockedMilestone.MILESTONE_1.mockProcessed(tangle, true);
        MockedMilestone.MILESTONE_2.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_3.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_4.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_5.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_6.mockProcessed(tangle, false);

        assertLatestProcessedSolidMilestoneEquals(MockedMilestone.MILESTONE_1);
    }

    @Test
    public void findLatestProcessedSolidMilestoneInDatabaseNearStart() throws Exception {
        MockedMilestone.MILESTONE_1.mockProcessed(tangle, true);
        MockedMilestone.MILESTONE_2.mockProcessed(tangle, true);
        MockedMilestone.MILESTONE_3.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_4.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_5.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_6.mockProcessed(tangle, false);

        assertLatestProcessedSolidMilestoneEquals(MockedMilestone.MILESTONE_2);
    }

    @Test
    public void findLatestProcessedSolidMilestoneInDatabaseInMiddle() throws Exception {
        MockedMilestone.MILESTONE_1.mockProcessed(tangle, true);
        MockedMilestone.MILESTONE_2.mockProcessed(tangle, true);
        MockedMilestone.MILESTONE_3.mockProcessed(tangle, true);
        MockedMilestone.MILESTONE_4.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_5.mockProcessed(tangle, false);
        MockedMilestone.MILESTONE_6.mockProcessed(tangle, false);

        assertLatestProcessedSolidMilestoneEquals(MockedMilestone.MILESTONE_3);
    }

    private void assertLatestProcessedSolidMilestoneEquals(MockedMilestone mockedMilestone) throws MilestoneException {
        Optional<MilestoneViewModel> latestMilestone = milestoneService.findLatestProcessedSolidMilestoneInDatabase();
        if (latestMilestone.isPresent()) {
            if (mockedMilestone == null) {
                Assert.fail("expected to find no latest processed solid milestone");
            } else {
                Assert.assertEquals((long) latestMilestone.get().index(), mockedMilestone.milestoneIndex);
            }
        } else {
            if (mockedMilestone != null) {
                Assert.fail("expected to find a latest processed solid milestone");
            }
        }
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////
}
