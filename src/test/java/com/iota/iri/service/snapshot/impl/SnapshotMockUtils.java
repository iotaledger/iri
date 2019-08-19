package com.iota.iri.service.snapshot.impl;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotProvider;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

public class SnapshotMockUtils {
    private static final int DEFAULT_MILESTONE_START_INDEX = 70000;

    private static final Hash DEFAULT_GENESIS_HASH = Hash.NULL_HASH;

    private static final Hash DEFAULT_GENESIS_ADDRESS = Hash.NULL_HASH;

    private static final long DEFAULT_GENESIS_TIMESTAMP = 1522146728;

    //region [mockSnapshotProvider] ////////////////////////////////////////////////////////////////////////////////////

    /**
     * Properly imitates a snapshot provider  by making a real initial and latest snapshot.
     * The balance of this provider is made to let the DEFAULT_GENESIS_ADDRESS (Null hash) have the entire IOTA supply.
     * Genesis timestamp set to {@value #DEFAULT_GENESIS_TIMESTAMP}.
     * Initial snapshot hash set to DEFAULT_GENESIS_ADDRESS.
     * Starting index is {@value #DEFAULT_MILESTONE_START_INDEX}
     * 
     * @param snapshotProvider The provider we are mocking. Must be a Mockito Mocked object
     * @return The supplied snapshotProvider object
     */
    public static SnapshotProvider mockSnapshotProvider(SnapshotProvider snapshotProvider) {
        return mockSnapshotProvider(snapshotProvider, DEFAULT_MILESTONE_START_INDEX);
    }

    /**
     * Properly imitates a snapshot provider  by making a real initial and latest snapshot.
     * The balance of this provider is made to let the DEFAULT_GENESIS_ADDRESS (Null hash) have the entire IOTA supply.
     * Genesis timestamp set to {@value #DEFAULT_GENESIS_TIMESTAMP}.
     * Initial snapshot hash set to DEFAULT_GENESIS_ADDRESS.
     * 
     * @param snapshotProvider The provider we are mocking. Must be a Mockito Mocked object
     * @param milestoneStartIndex The index we use for the genesis/initial snapshot
     * @return The supplied snapshotProvider object
     */
    public static SnapshotProvider mockSnapshotProvider(SnapshotProvider snapshotProvider, int milestoneStartIndex) {
        return mockSnapshotProvider(snapshotProvider, milestoneStartIndex, DEFAULT_GENESIS_HASH);
    }

    /**
     * Properly imitates a snapshot provider  by making a real initial and latest snapshot.
     * The balance of this provider is made to let the DEFAULT_GENESIS_ADDRESS (Null hash) have the entire IOTA supply.
     * Genesis timestamp set to {@value #DEFAULT_GENESIS_TIMESTAMP}
     * 
     * @param snapshotProvider The provider we are mocking. Must be a Mockito Mocked object
     * @param milestoneStartIndex The index we use for the genesis/initial snapshot
     * @param genesisHash The Genesis hash
     * @return The supplied snapshotProvider object
     */
    public static SnapshotProvider mockSnapshotProvider(SnapshotProvider snapshotProvider, int milestoneStartIndex,
            Hash genesisHash) {

        return mockSnapshotProvider(snapshotProvider, milestoneStartIndex, genesisHash, DEFAULT_GENESIS_TIMESTAMP);
    }

    /**
     * Properly imitates a snapshot provider  by making a real initial and latest snapshot.
     * The balance of this provider is made to let the DEFAULT_GENESIS_ADDRESS (Null hash) have the entire IOTA supply.
     * 
     * @param snapshotProvider The provider we are mocking. Must be a Mockito Mocked object
     * @param milestoneStartIndex The index we use for the genesis/initial snapshot
     * @param genesisHash The Genesis hash
     * @param genesisTimestamp The timestamp of the initial snapshot creation
     * @return The supplied snapshotProvider object
     */
    public static SnapshotProvider mockSnapshotProvider(SnapshotProvider snapshotProvider, int milestoneStartIndex,
            Hash genesisHash, long genesisTimestamp) {

        Map<Hash, Long> balances = new HashMap<>();
        balances.put(DEFAULT_GENESIS_ADDRESS, TransactionViewModel.SUPPLY);

        return mockSnapshotProvider(snapshotProvider, milestoneStartIndex, genesisHash, genesisTimestamp, balances);
    }

    /**
     * Properly imitates a snapshot provider by making a real initial and latest snapshot
     * 
     * @param snapshotProvider The provider we are mocking. Must be a Mockito Mocked object
     * @param milestoneStartIndex The index we use for the genesis/initial snapshot
     * @param genesisHash The Genesis hash
     * @param genesisTimestamp The timestamp of the initial snapshot creation
     * @param balances The balances to add to the provider
     * @return The supplied snapshotProvider object
     */
    public static SnapshotProvider mockSnapshotProvider(SnapshotProvider snapshotProvider, int milestoneStartIndex,
            Hash genesisHash, long genesisTimestamp, Map<Hash, Long> balances) {

        Map<Hash, Integer> solidEntryPoints = new HashMap<>();
        solidEntryPoints.put(genesisHash, milestoneStartIndex);

        Snapshot initialSnapshot = new SnapshotImpl(
                new SnapshotStateImpl(balances),
                new SnapshotMetaDataImpl(genesisHash, milestoneStartIndex, genesisTimestamp, solidEntryPoints,
                        new HashMap<>())
        );
        Snapshot latestSnapshot = initialSnapshot.clone();

        Mockito.when(snapshotProvider.getInitialSnapshot()).thenReturn(initialSnapshot);
        Mockito.when(snapshotProvider.getLatestSnapshot()).thenReturn(latestSnapshot);

        return snapshotProvider;
    }

    //endregion ////////////////////////////////////////////////////////////////////////////////////////////////////////
}
