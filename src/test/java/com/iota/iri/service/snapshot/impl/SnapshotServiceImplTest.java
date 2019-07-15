package com.iota.iri.service.snapshot.impl;

import com.iota.iri.TangleMockUtils;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
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

import java.util.HashMap;
import java.util.Map;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SnapshotServiceImplTest {
    //region [CONSTANTS FOR THE TEST] //////////////////////////////////////////////////////////////////////////////////

    @Rule 
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    
    private enum MockedMilestone {
        A("ARWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 70001, 1542146728L),
        B("BRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB", 70005, 1546146728L);

        private final Hash transactionHash;

        private final int milestoneIndex;

        private final long timestamp;

        MockedMilestone(String transactionHash, int milestoneIndex, long timestamp) {
            this.transactionHash = HashFactory.TRANSACTION.create(transactionHash);
            this.milestoneIndex = milestoneIndex;
            this.timestamp = timestamp;
        }

        public void mock(Tangle tangle, Map<Hash, Long> stateDiff) {
            TangleMockUtils.mockMilestone(tangle, transactionHash, milestoneIndex);
            TangleMockUtils.mockStateDiff(tangle, transactionHash, stateDiff);
            Transaction mockedTransaction = TangleMockUtils.mockTransaction(tangle, transactionHash);
            mockedTransaction.timestamp = timestamp;
        }
    }

    private static final Hash ADDRESS_1 = HashFactory.ADDRESS.create(
            "EKRQUHQRZWDGFTRFSTSPAZYBXMEYGHOFIVXDCRRTXUJ9HXOAYLKFEBEZPWEPTG9ZFTOHGCQZCHIKKQ9RD");

    private static final Hash ADDRESS_2 = HashFactory.ADDRESS.create(
            "GRWY9LWHXEWNL9DTN9IGMIMIVSBQUIEIDSFRYTCSXQARRTVEUFSBWFZRQOJUQNAGQLWHTFNVECELCOFYB");

    private static final Hash ADDRESS_3 = HashFactory.ADDRESS.create(
            "JLDULQUXBL99AGZZKXMACLJRAYDUTBTMFGLEHVTLDTHVUIBYV9ZKGHLWCVFJVIYGHNXNTQUYQTISHDUSW");

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region [BOILERPLATE] /////////////////////////////////////////////////////////////////////////////////////////////

    @Mock
    private Tangle tangle;

    @Mock
    private SnapshotProvider snapshotProvider;

    @InjectMocks
    private SnapshotServiceImpl snapshotService;

    @Before
    public void setUp() {
        SnapshotMockUtils.mockSnapshotProvider(snapshotProvider);

        MilestoneViewModel.clear();
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region [TEST: replayMilestones] //////////////////////////////////////////////////////////////////////////////////

    @Test
    public void replayMilestonesSingle() throws Exception {
        Snapshot latestSnapshot = snapshotProvider.getLatestSnapshot();

        MockedMilestone.A.mock(tangle, createBalanceMap(
                Hash.NULL_HASH, -2337L,
                ADDRESS_1, 1337L,
                ADDRESS_2, 1000L
        ));

        snapshotService.replayMilestones(latestSnapshot, MockedMilestone.A.milestoneIndex);

        Assert.assertEquals("the snapshot should have the milestone index of the last applied milestone",
                MockedMilestone.A.milestoneIndex, latestSnapshot.getIndex());

        Assert.assertEquals("the snapshot should have the transaction hash of the last applied milestone",
                MockedMilestone.A.transactionHash, latestSnapshot.getHash());

        Assert.assertEquals("the snapshot should have the timestamp of the last applied milestone",
                MockedMilestone.A.timestamp, latestSnapshot.getTimestamp());

        Assert.assertEquals("the balance of the addresses should reflect the accumulated changes of the milestones",
                TransactionViewModel.SUPPLY - 1337L - 1000L, (long) latestSnapshot.getBalance(Hash.NULL_HASH));

        Assert.assertEquals("the balance of the addresses should reflect the accumulated changes of the milestones",
                1337L, (long) latestSnapshot.getBalance(ADDRESS_1));

        Assert.assertEquals("the balance of the addresses should reflect the accumulated changes of the milestones",
                1000L, (long) latestSnapshot.getBalance(ADDRESS_2));
    }

    @Test
    public void replayMilestonesMultiple() throws Exception {
        Snapshot latestSnapshot = snapshotProvider.getLatestSnapshot();

        MockedMilestone.A.mock(tangle, createBalanceMap(
                Hash.NULL_HASH, -2337L,
                ADDRESS_1,       1337L,
                ADDRESS_2,       1000L
        ));
        MockedMilestone.B.mock(tangle, createBalanceMap(
                Hash.NULL_HASH, -1234L,
                ADDRESS_2,       1000L,
                ADDRESS_3,        234L
        ));

        snapshotService.replayMilestones(latestSnapshot, MockedMilestone.B.milestoneIndex);

        Assert.assertEquals("the snapshot should have the milestone index of the last applied milestone",
                MockedMilestone.B.milestoneIndex, latestSnapshot.getIndex());

        Assert.assertEquals("the snapshot should have the transaction hash of the last applied milestone",
                MockedMilestone.B.transactionHash, latestSnapshot.getHash());

        Assert.assertEquals("the snapshot should have the timestamp of the last applied milestone",
                MockedMilestone.B.timestamp, latestSnapshot.getTimestamp());

        Assert.assertEquals("the balance of the addresses should reflect the accumulated changes of the milestones",
                TransactionViewModel.SUPPLY - 1337L - 2000L - 234L, (long) latestSnapshot.getBalance(Hash.NULL_HASH));

        Assert.assertEquals("the balance of the addresses should reflect the accumulated changes of the milestones",
                1337L, (long) latestSnapshot.getBalance(ADDRESS_1));

        Assert.assertEquals("the balance of the addresses should reflect the accumulated changes of the milestones",
                2000L, (long) latestSnapshot.getBalance(ADDRESS_2));

        Assert.assertEquals("the balance of the addresses should reflect the accumulated changes of the milestones",
                234L, (long) latestSnapshot.getBalance(ADDRESS_3));
    }

    @Test
    public void replayMilestonesInconsistent() {
        Snapshot initialSnapshot = snapshotProvider.getInitialSnapshot();
        Snapshot latestSnapshot = snapshotProvider.getLatestSnapshot();

        MockedMilestone.A.mock(tangle, createBalanceMap(
                Hash.NULL_HASH, -2337L,
                ADDRESS_1,       1337L,
                ADDRESS_2,       1000L
        ));

        // create inconsistent milestone (the sum of the balance changes is not "0")
        MockedMilestone.B.mock(tangle, createBalanceMap(
                Hash.NULL_HASH, -1234L,
                ADDRESS_2,       1000L,
                ADDRESS_3,       1234L
        ));

        try {
            snapshotService.replayMilestones(latestSnapshot, MockedMilestone.B.milestoneIndex);

            Assert.fail("replaying inconsistent milestones should raise an exception");
        } catch (SnapshotException e) {
            Assert.assertEquals("failed replays should not modify the snapshot", initialSnapshot, latestSnapshot);
        }
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region [TEST: rollbackMilestones] ////////////////////////////////////////////////////////////////////////////////

    @Test
    public void rollbackMilestonesSingle() throws Exception {
        Snapshot latestSnapshot = snapshotProvider.getLatestSnapshot();

        replayMilestonesMultiple();

        snapshotService.rollBackMilestones(latestSnapshot, MockedMilestone.B.milestoneIndex);

        Assert.assertEquals("the snapshot should have the milestone index of the milestone that we rolled back to",
                MockedMilestone.A.milestoneIndex, latestSnapshot.getIndex());

        Assert.assertEquals("the snapshot should have the transaction hash of the milestone that we rolled back to",
                MockedMilestone.A.transactionHash, latestSnapshot.getHash());

        Assert.assertEquals("the snapshot should have the timestamp of the milestone that we rolled back to",
                MockedMilestone.A.timestamp, latestSnapshot.getTimestamp());

        Assert.assertEquals("the balance of the addresses should reflect the balances of the rolled back milestone",
                TransactionViewModel.SUPPLY - 1337L - 1000L, (long) latestSnapshot.getBalance(Hash.NULL_HASH));

        Assert.assertEquals("the balance of the addresses should reflect the balances of the rolled back milestone",
                1337L, (long) latestSnapshot.getBalance(ADDRESS_1));

        Assert.assertEquals("the balance of the addresses should reflect the balances of the rolled back milestone",
                1000L, (long) latestSnapshot.getBalance(ADDRESS_2));
    }

    @Test
    public void rollbackMilestonesAll() throws Exception {
        Snapshot initialSnapshot = snapshotProvider.getInitialSnapshot();
        Snapshot latestSnapshot = snapshotProvider.getLatestSnapshot();

        replayMilestonesMultiple();

        snapshotService.rollBackMilestones(latestSnapshot, MockedMilestone.A.milestoneIndex);

        Assert.assertEquals("rolling back all milestones should revert all changes", initialSnapshot, latestSnapshot);
    }

    @Test
    public void rollbackMilestonesInvalidIndex() throws Exception {
        Snapshot initialSnapshot = snapshotProvider.getInitialSnapshot();
        Snapshot latestSnapshot = snapshotProvider.getLatestSnapshot();

        replayMilestonesMultiple();

        Snapshot clonedSnapshot = latestSnapshot.clone();

        Assert.assertEquals("the cloned snapshots should be equal to the source", clonedSnapshot, latestSnapshot);

        try {
            snapshotService.rollBackMilestones(latestSnapshot, latestSnapshot.getIndex() + 1);

            Assert.fail("rolling back non-applied milestones should fail");
        } catch (SnapshotException e) {
            Assert.assertEquals("failed rollbacks should not modify the snapshot", clonedSnapshot, latestSnapshot);
        }

        try {
            snapshotService.rollBackMilestones(latestSnapshot, initialSnapshot.getIndex());

            Assert.fail("rolling back milestones prior to the genesis should fail");
        } catch (SnapshotException e) {
            Assert.assertEquals("failed rollbacks should not modify the snapshot", clonedSnapshot, latestSnapshot);
        }
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////

    //region [UTILITY METHODS] /////////////////////////////////////////////////////////////////////////////////////////

    private static <KEY, VALUE> Map<KEY, VALUE> createBalanceMap(Object... mapEntries) {
        Map<KEY, VALUE> result = new HashMap<>();

        for (int i = 0; i < mapEntries.length / 2; i++) {
            result.put((KEY) mapEntries[i * 2], (VALUE) mapEntries[i * 2 + 1]);
        }

        return result;
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////
}
