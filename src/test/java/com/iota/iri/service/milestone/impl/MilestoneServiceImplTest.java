package com.iota.iri.service.milestone.impl;

import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.persistables.Milestone;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.service.milestone.MilestoneException;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.SnapshotService;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Pair;
import com.iota.iri.zmq.MessageQ;
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

@RunWith(MockitoJUnitRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MilestoneServiceImplTest {
    //region [BOILERPLATE] /////////////////////////////////////////////////////////////////////////////////////////////

    private static MilestoneServiceImpl milestoneService;

    @Mock
    private static Tangle tangle;

    @Mock
    private static SnapshotProvider snapshotProvider;

    @Mock
    private static SnapshotService snapshotService;

    @Mock
    private static MessageQ messageQ;

    @BeforeClass
    public static void setupClass() {
        tangle = Mockito.mock(Tangle.class);
        snapshotProvider = Mockito.mock(SnapshotProvider.class);
        snapshotService = Mockito.mock(SnapshotService.class);
        messageQ = Mockito.mock(MessageQ.class);

        milestoneService = new MilestoneServiceImpl().init(tangle, snapshotProvider, snapshotService, messageQ,
                new MainnetConfig());

        mockSnapshotProvider();
    }

    @Before
    public void setupTest() {
        MilestoneViewModel.clear();
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region [TESTS] ///////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void findLatestProcessedSolidMilestoneInDatabase_atEnd() throws Exception {
        // hash / index / processed
        mockMilestones(
                "ARWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 1,  true,
                "BRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 2,  false,
                "CRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 3,  false,
                "JRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 10, false,
                "KRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 11, false,
                "LRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 12, true
        );

        checkLatestProcessedSolidMilestone(12);
    }

    @Test
    public void findLatestProcessedSolidMilestoneInDatabase_nearEnd() throws Exception {
        // hash / index / processed
        mockMilestones(
                "ARWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 1,  true,
                "BRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 2,  false,
                "CRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 3,  false,
                "JRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 10, false,
                "KRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 11, true,
                "LRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 12, false
        );

        checkLatestProcessedSolidMilestone(11);
    }

    @Test
    public void findLatestProcessedSolidMilestoneInDatabase_atStart() throws Exception {
        // hash / index / processed
        mockMilestones(
                "ARWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 1,  true,
                "BRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 2,  false,
                "CRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 3,  false,
                "JRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 10, false,
                "KRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 11, false,
                "LRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 12, false
        );

        checkLatestProcessedSolidMilestone(1);
    }

    @Test
    public void findLatestProcessedSolidMilestoneInDatabase_nearStart() throws Exception {
        // hash / index / processed
        mockMilestones(
                "ARWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 1,  true,
                "BRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 2,  true,
                "CRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 3,  false,
                "JRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 10, false,
                "KRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 11, false,
                "LRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 12, false
        );

        checkLatestProcessedSolidMilestone(2);
    }

    @Test
    public void findLatestProcessedSolidMilestoneInDatabase_inMiddle() throws Exception {
        // hash / index / processed
        mockMilestones(
                "ARWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 1,  true,
                "BRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 2,  true,
                "CRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 3,  true,
                "JRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 10, false,
                "KRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 11, false,
                "LRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 12, false
        );

        checkLatestProcessedSolidMilestone(3);
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region [UTILITY METHODS] /////////////////////////////////////////////////////////////////////////////////////////

    private static void checkLatestProcessedSolidMilestone(int index) throws MilestoneException {
        milestoneService.findLatestProcessedSolidMilestoneInDatabase().ifPresent(milestone -> {
            try {
                Assert.assertEquals((long) milestone.index(), ((Milestone) tangle.load(Milestone.class,
                        new IntegerIndex(index))).index.getValue());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void mockSnapshotProvider() {
        Snapshot initialSnapshot = Mockito.mock(Snapshot.class);

        Mockito.when(initialSnapshot.getIndex()).thenReturn(0);
        Mockito.when(snapshotProvider.getInitialSnapshot()).thenReturn(initialSnapshot);
    }

    private static void mockMilestones(Object... params) throws Exception {
        Pair<Indexable, Persistable> latestMilestone = null;
        for (int i = 0; i < params.length / 3; i++) {
            latestMilestone = mockMilestone(
                    (String)  params[i * 3    ],
                    (int)     params[i * 3 + 1],
                    (boolean) params[i * 3 + 2]
            );
        }

        Mockito.when(tangle.getLatest(Milestone.class, IntegerIndex.class)).thenReturn(latestMilestone);
    }

    private static Pair<Indexable, Persistable> mockMilestone(String hash, int index, boolean applied) throws
            Exception {

        Milestone latestMilestone = new Milestone();
        latestMilestone.hash = HashFactory.TRANSACTION.create(hash);
        latestMilestone.index = new IntegerIndex(index);

        Mockito.when(tangle.load(Milestone.class, new IntegerIndex(index))).thenReturn(latestMilestone);

        Transaction latestMilestoneTransaction = new Transaction();
        latestMilestoneTransaction.bytes = new byte[0];
        latestMilestoneTransaction.type = TransactionViewModel.FILLED_SLOT;
        latestMilestoneTransaction.snapshot = applied ? index : 0;
        latestMilestoneTransaction.parsed = true;

        Mockito.when(tangle.load(Transaction.class, latestMilestone.hash)).thenReturn(latestMilestoneTransaction);

        return new Pair<>(latestMilestone.index, latestMilestone);
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////
}
