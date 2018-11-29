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

    public static SnapshotProvider mockSnapshotProvider(SnapshotProvider snapshotProvider) {
        return mockSnapshotProvider(snapshotProvider, DEFAULT_MILESTONE_START_INDEX);
    }

    public static SnapshotProvider mockSnapshotProvider(SnapshotProvider snapshotProvider, int milestoneStartIndex) {
        return mockSnapshotProvider(snapshotProvider, milestoneStartIndex, DEFAULT_GENESIS_HASH);
    }

    public static SnapshotProvider mockSnapshotProvider(SnapshotProvider snapshotProvider, int milestoneStartIndex,
            Hash genesisHash) {

        return mockSnapshotProvider(snapshotProvider, milestoneStartIndex, genesisHash, DEFAULT_GENESIS_TIMESTAMP);
    }

    public static SnapshotProvider mockSnapshotProvider(SnapshotProvider snapshotProvider, int milestoneStartIndex,
            Hash genesisHash, long genesisTimestamp) {

        Map<Hash, Long> balances = new HashMap<>();
        balances.put(DEFAULT_GENESIS_ADDRESS, TransactionViewModel.SUPPLY);

        return mockSnapshotProvider(snapshotProvider, milestoneStartIndex, genesisHash, genesisTimestamp, balances);
    }

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
