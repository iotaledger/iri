package com.iota.iri.storage.rocksDB;

import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.LocalSnapshot;
import com.iota.iri.model.persistables.SpentAddress;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.impl.SnapshotMockUtils;
import com.iota.iri.storage.LocalSnapshotsPersistenceProvider;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.Assert;
import org.junit.rules.TemporaryFolder;

public class LocalSnapshotsPersistenceProviderTest {
    private final TemporaryFolder dbFolder = new TemporaryFolder();
    private final TemporaryFolder logFolder = new TemporaryFolder();


    private LocalSnapshotsPersistenceProvider provider;
    private static final IntegerIndex LS_KEY = new IntegerIndex(1);
    private static Hash milestoneHash = HashFactory.TRANSACTION.create("MILESTONE");



    @Before
    public void setUp() throws Exception {
        dbFolder.create();
        logFolder.create();
        provider = new LocalSnapshotsPersistenceProvider(
                new RocksDBPersistenceProvider(
                        dbFolder.getRoot().getAbsolutePath(),
                        logFolder.getRoot().getAbsolutePath(),
                        1000,
                        LocalSnapshotsPersistenceProvider.COLUMN_FAMILIES,
                        null));
        provider.init();
    }

    @After
    public void tearDown() {
        provider.shutdown();
    }

    @Test
    public void startMakesAvailable(){
        Assert.assertTrue("Expected provider to be available", provider.isAvailable());
    }

    @Test
    public void localSnapshotStoredSuccessfully() throws Exception {
        LocalSnapshot localSnapshot = makeLocalSnapshot();

        //Save
        Assert.assertTrue("Expected the local snapshot to be saved in the db",
                provider.save(localSnapshot, LS_KEY));

        //Exists
        Assert.assertTrue("Expected the local snapshot to exist in the db",
                provider.exists(LocalSnapshot.class, LS_KEY));

        //Get
        LocalSnapshot loadedLocalSnapshot = (LocalSnapshot) provider.get(LocalSnapshot.class, LS_KEY);
        Assert.assertEquals("Expected the loaded local snapshot hash to be equal to the saved local snapshot hash",
                localSnapshot.milestoneHash, loadedLocalSnapshot.milestoneHash);
        Assert.assertEquals("Expected the loaded local snapshot index to be equal to the saved local snapshot index",
                localSnapshot.milestoneIndex, loadedLocalSnapshot.milestoneIndex);
        Assert.assertEquals("Expected the loaded local snapshot timestamp to be equal to the saved local snapshot timestamp",
                localSnapshot.milestoneTimestamp, loadedLocalSnapshot.milestoneTimestamp);
    }

    @Test
    public void spentAddressesStoredSuccessfully() throws Exception {
        Hash spentAddress = HashFactory.ADDRESS.create("SPEND9ONE");
        Hash spentAddress2 = HashFactory.ADDRESS.create("SPEND9TWO");

        //Save
        Assert.assertTrue("Expected the spent address to be saved in the db",
                provider.save(new SpentAddress(), spentAddress));
        Assert.assertTrue("Expected the spent address to be saved in the db",
                provider.save(new SpentAddress(), spentAddress2));

        //Exists
        Assert.assertTrue("Expected the spent address to exist in the db",
                provider.exists(SpentAddress.class, spentAddress));
        Assert.assertTrue("Expected the spent address to exist in the db",
                provider.exists(SpentAddress.class, spentAddress2));

        //Get
        SpentAddress loadedSpentAddress = (SpentAddress) provider.get(SpentAddress.class, spentAddress);
        SpentAddress loadedSpentAddress2 = (SpentAddress) provider.get(SpentAddress.class, spentAddress2);

        Assert.assertTrue("Expected the loaded spent address to exist", loadedSpentAddress.exists());
        Assert.assertTrue("Expected the loaded spent address to exist", loadedSpentAddress2.exists());

    }


    private LocalSnapshot makeLocalSnapshot(){
        Snapshot snapshot = SnapshotMockUtils.createSnapshot(1, milestoneHash);
        LocalSnapshot localSnapshot = new LocalSnapshot();
        localSnapshot.milestoneHash = milestoneHash;
        localSnapshot.ledgerState = snapshot.getBalances();
        localSnapshot.milestoneIndex = snapshot.getIndex();
        localSnapshot.milestoneTimestamp = snapshot.getTimestamp();
        localSnapshot.seenMilestones = snapshot.getSeenMilestones();
        localSnapshot.solidEntryPoints = snapshot.getSolidEntryPoints();
        return localSnapshot;
    }

}
