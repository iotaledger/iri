package com.iota.iri.service.milestone.impl;

import com.iota.iri.TangleMockUtils;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.impl.SnapshotMockUtils;
import com.iota.iri.storage.Tangle;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MilestoneServiceImplTest {
    //region [CONSTANTS FOR THE TEST] //////////////////////////////////////////////////////////////////////////////////

    @Rule 
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    
    private enum MockedMilestone {
        A("ARWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 70001),
        B("BRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 70002),
        C("CRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 70003),
        D("JRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 70010),
        E("KRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 70011),
        F("LRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 70012);

        private final Hash transactionHash;

        private final int milestoneIndex;

        MockedMilestone(String transactionHash, int milestoneIndex) {
            this.transactionHash = HashFactory.TRANSACTION.create(transactionHash);
            this.milestoneIndex = milestoneIndex;
        }

        public void mockProcessed(Tangle tangle, boolean applied) {
            TangleMockUtils.mockMilestone(tangle, transactionHash, milestoneIndex);
            Transaction mockedTransaction = TangleMockUtils.mockTransaction(tangle, transactionHash);
            mockedTransaction.snapshot = applied ? milestoneIndex : 0;
        }
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region [BOILERPLATE] /////////////////////////////////////////////////////////////////////////////////////////////

    @Mock
    private Tangle tangle;

    @Mock
    private SnapshotProvider snapshotProvider;

    @InjectMocks
    private MilestoneServiceImpl milestoneService;

    @Before
    public void setUp() {
        SnapshotMockUtils.mockSnapshotProvider(snapshotProvider);

        MilestoneViewModel.clear();
    }

    @After
    public void tearDown() {
        MilestoneViewModel.clear();
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region [TEST: findLatestProcessedSolidMilestoneInDatabase] ///////////////////////////////////////////////////////

    @Test
    public void findLatestProcessedSolidMilestoneInDatabaseNone() throws Exception {
        MockedMilestone.A.mockProcessed(tangle, false);
        MockedMilestone.B.mockProcessed(tangle, false);
        MockedMilestone.C.mockProcessed(tangle, false);
        MockedMilestone.D.mockProcessed(tangle, false);
        MockedMilestone.E.mockProcessed(tangle, false);
        MockedMilestone.F.mockProcessed(tangle, false);

        Optional<MilestoneViewModel> latestMilestone = milestoneService.findLatestProcessedSolidMilestoneInDatabase();
        if (latestMilestone.isPresent()) {
            Assert.fail("expected to find no latest processed solid milestone");
        }
    }

    @Test
    public void findLatestProcessedSolidMilestoneInDatabaseAtEnd() throws Exception {
        MockedMilestone.A.mockProcessed(tangle, true);
        MockedMilestone.B.mockProcessed(tangle, false);
        MockedMilestone.C.mockProcessed(tangle, false);
        MockedMilestone.D.mockProcessed(tangle, false);
        MockedMilestone.E.mockProcessed(tangle, false);
        MockedMilestone.F.mockProcessed(tangle, true);

        Optional<MilestoneViewModel> latestMilestone = milestoneService.findLatestProcessedSolidMilestoneInDatabase();
        if (latestMilestone.isPresent()) {
            Assert.assertEquals((long) latestMilestone.get().index(), MockedMilestone.F.milestoneIndex);
        } else {
            Assert.fail("expected to find a latest processed solid milestone");
        }
    }

    @Test
    public void findLatestProcessedSolidMilestoneInDatabaseNearEnd() throws Exception {
        MockedMilestone.A.mockProcessed(tangle, true);
        MockedMilestone.B.mockProcessed(tangle, false);
        MockedMilestone.C.mockProcessed(tangle, false);
        MockedMilestone.D.mockProcessed(tangle, false);
        MockedMilestone.E.mockProcessed(tangle, true);
        MockedMilestone.F.mockProcessed(tangle, false);

        Optional<MilestoneViewModel> latestMilestone = milestoneService.findLatestProcessedSolidMilestoneInDatabase();
        if (latestMilestone.isPresent()) {
            Assert.assertEquals((long) latestMilestone.get().index(), MockedMilestone.E.milestoneIndex);
        } else {
            Assert.fail("expected to find a latest processed solid milestone");
        }
    }

    @Test
    public void findLatestProcessedSolidMilestoneInDatabaseAtStart() throws Exception {
        MockedMilestone.A.mockProcessed(tangle, true);
        MockedMilestone.B.mockProcessed(tangle, false);
        MockedMilestone.C.mockProcessed(tangle, false);
        MockedMilestone.D.mockProcessed(tangle, false);
        MockedMilestone.E.mockProcessed(tangle, false);
        MockedMilestone.F.mockProcessed(tangle, false);

        Optional<MilestoneViewModel> latestMilestone = milestoneService.findLatestProcessedSolidMilestoneInDatabase();
        if (latestMilestone.isPresent()) {
            Assert.assertEquals((long) latestMilestone.get().index(), MockedMilestone.A.milestoneIndex);
        } else {
            Assert.fail("expected to find a latest processed solid milestone");
        }
    }

    @Test
    public void findLatestProcessedSolidMilestoneInDatabaseNearStart() throws Exception {
        MockedMilestone.A.mockProcessed(tangle, true);
        MockedMilestone.B.mockProcessed(tangle, true);
        MockedMilestone.C.mockProcessed(tangle, false);
        MockedMilestone.D.mockProcessed(tangle, false);
        MockedMilestone.E.mockProcessed(tangle, false);
        MockedMilestone.F.mockProcessed(tangle, false);

        Optional<MilestoneViewModel> latestMilestone = milestoneService.findLatestProcessedSolidMilestoneInDatabase();
        if (latestMilestone.isPresent()) {
            Assert.assertEquals((long) latestMilestone.get().index(), MockedMilestone.B.milestoneIndex);
        } else {
            Assert.fail("expected to find a latest processed solid milestone");
        }
    }

    @Test
    public void findLatestProcessedSolidMilestoneInDatabaseInMiddle() throws Exception {
        MockedMilestone.A.mockProcessed(tangle, true);
        MockedMilestone.B.mockProcessed(tangle, true);
        MockedMilestone.C.mockProcessed(tangle, true);
        MockedMilestone.D.mockProcessed(tangle, false);
        MockedMilestone.E.mockProcessed(tangle, false);
        MockedMilestone.F.mockProcessed(tangle, false);

        Optional<MilestoneViewModel> latestMilestone = milestoneService.findLatestProcessedSolidMilestoneInDatabase();
        if (latestMilestone.isPresent()) {
            Assert.assertEquals((long) latestMilestone.get().index(), MockedMilestone.C.milestoneIndex);
        } else {
            Assert.fail("expected to find a latest processed solid milestone");
        }
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////
}
