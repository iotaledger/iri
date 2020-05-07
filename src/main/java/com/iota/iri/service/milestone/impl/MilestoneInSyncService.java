package com.iota.iri.service.milestone.impl;

import com.iota.iri.service.milestone.InSyncService;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.snapshot.SnapshotProvider;

import com.google.common.annotations.VisibleForTesting;

/**
 * 
 * A node is defined in sync when the latest snapshot milestone index and the
 * latest milestone index are equal. In order to prevent a bounce between in and
 * out of sync, a buffer is added when a node became in sync.
 * 
 * This will always return false if we are not done scanning milestone
 * candidates during initialization.
 *
 */
public class MilestoneInSyncService implements InSyncService {
    
    /**
     * To prevent jumping back and forth in and out of sync, there is a buffer in between.
     * Only when the latest milestone and latest snapshot differ more than this number, we fall out of sync
     */
    @VisibleForTesting
    static final int LOCAL_SNAPSHOT_SYNC_BUFFER = 5;
    
    /**
     * If this node is currently seen as in sync
     */
    private boolean isInSync;
    
    /**
     * Data provider for the latest solid index
     */
    private SnapshotProvider snapshotProvider;
    
    /**
     * Data provider for the latest index
     */
    private MilestoneSolidifier milestoneSolidifier;
    
    /**
     * @param snapshotProvider data provider for the snapshots that are relevant for the node
     */
    public MilestoneInSyncService(SnapshotProvider snapshotProvider, MilestoneSolidifier milestoneSolidifier) {
        this.snapshotProvider = snapshotProvider;
        this.milestoneSolidifier = milestoneSolidifier;
        this.isInSync = false;
    }

    @Override
    public boolean isInSync() {
        if (!milestoneSolidifier.isInitialScanComplete()) {
            return false;
        }

        int latestIndex = milestoneSolidifier.getLatestMilestoneIndex();
        int latestSnapshot = snapshotProvider.getLatestSnapshot().getIndex();

        // If we are out of sync, only a full sync will get us in
        if (!isInSync && latestIndex == latestSnapshot) {
            isInSync = true;

        // When we are in sync, only dropping below the buffer gets us out of sync
        } else if (latestSnapshot < latestIndex - LOCAL_SNAPSHOT_SYNC_BUFFER) {
            isInSync = false;
        }

        return isInSync;
    }

}
