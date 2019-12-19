package com.iota.iri.service.snapshot.conditions;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.persistables.Milestone;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.IotaUtils;
import com.iota.iri.utils.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * Conditions for snapshotting based on the size of the database on disk.
 * This does not take into account the size kept in memory, and can be inaccurate if the node is inactive.
 *
 */
public class SnapshotSizeCondition implements SnapshotCondition {

    private static final Logger log = LoggerFactory.getLogger(SnapshotSizeCondition.class);
    
    /**
     * Percentage margin for the database max size due to 
     */
    private static final double MARGIN = 1;
    
    /**
     * Amount of milestones we snapshot when the DB is too large
     */
    private static final int MILESTONES = 5;
    
    /**
     * The maximum size we want the DB to have, with margin due to DB delays for writing to disk
     */
    private final long maxSize;
    
    /**
     * Database object we use to find the lowest milestone
     */
    private final Tangle tangle;
    
    /**
     * The cached size of the db, so we don't keep snapshotting when the size doesn't change after a snapshot 
     */
    private long lastSize = -1;

    /**
     * Implements a {@link SnapshotCondition} based on the total database size
     * 
     * @param tangle the database interface.
     * @param config configuration with snapshot specific settings.
     * @param snapshotProvider gives us access to the relevant snapshots.
     */
    public SnapshotSizeCondition(Tangle tangle, SnapshotConfig config) {
        this.tangle = tangle;
        if (config.getLocalSnapshotsPruningEnabled()) {
            maxSize = (long) Math.floor(IotaUtils.parseFileSize(config.getLocalSnapshotsDbMaxSize()) / 100 * (100-MARGIN));
            
            if (config.getLocalSnapshotsPruningDelay() != BaseIotaConfig.Defaults.LOCAL_SNAPSHOTS_PRUNING_DELAY_MIN) {
                log.warn("We recommend setting pruning delay to the minimum(" 
                        + BaseIotaConfig.Defaults.LOCAL_SNAPSHOTS_PRUNING_DELAY_MIN 
                        + ") when using db size limitation.");
            }
        } else {
            if (config.getLocalSnapshotsDbMaxSize() != BaseIotaConfig.Defaults.LOCAL_SNAPSHOTS_DB_MAX_SIZE) {
                log.warn("Local snapshots with size condition does not work with pruning disabled");
            }
            maxSize = -1;
        }
    }

    /**
     * We only take a snapshot if the db size updated since last check and the new size is bigger than the maximum allowed.
     * A margin of 1% is taken, based on the min amount of milestones, resulting in ~10GB DB, 100MB margin.
     * This corresponds to the, default, 64MB buffer for writing to disk, which is at most 128 of size.
     * 
      * {@inheritDoc}
     */
    @Override
    public boolean shouldTakeSnapshot(boolean isInSync) {
        long size = tangle.getPersistanceSize();
        if (size == lastSize || maxSize <= 0) {
            return false;
        } 
        
        // Update the last size
        lastSize = size;
        
        return size > maxSize;
    }

    @Override
    public int getSnapshotStartingMilestone() throws SnapshotException {
        // Snapshot by size doesn't need a recent snapshot, we calculate here so we can reuse it when we call getSnapshotPruningMilestone.
        // This will not go below the allowed snapshot depth due to the other conditions
        return -1;
    }

    @Override
    public int getSnapshotPruningMilestone() throws SnapshotException {
        int initialIndex = -1;
        try {
            Pair<Indexable, Persistable> ms = tangle.getFirst(Milestone.class, IntegerIndex.class);
            initialIndex = ((IntegerIndex)ms.low).getValue();
        } catch (Exception e) {
            log.error("failed to find oldest milestone", e);
            // This should never fail, if it does, we just don't prune
            return -1;
        }
        
        return initialIndex + MILESTONES;
    }
}
