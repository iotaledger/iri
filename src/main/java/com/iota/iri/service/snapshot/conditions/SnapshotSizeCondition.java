package com.iota.iri.service.snapshot.conditions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.IotaUtils;

/**
 * 
 * 
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
    private final Tangle tangle;

    private final SnapshotProvider snapshotProvider;
    
    /**
     * The cached size of the db, so we don't keep snapshotting when the size doesn't change after a snapshot 
     */
    private long lastSize = -1;

    private SnapshotConfig config;

    /**
     * Implements a {@link SnapshotCondition} based on the total database size
     * 
     * @param tangle the database interface.
     * @param config configuration with snapshot specific settings.
     * @param snapshotProvider gives us access to the relevant snapshots.
     */
    public SnapshotSizeCondition(Tangle tangle, SnapshotConfig config, SnapshotProvider snapshotProvider) {
        this.tangle = tangle;
        this.config = config;
        if (config.getLocalSnapshotsPruningEnabled()) {
            maxSize = (long) Math.floor(IotaUtils.parseFileSize(config.getLocalSnapshotsDbMaxSize()) / 100 * (100-MARGIN));
        } else {
            if (config.getLocalSnapshotsDbMaxSize() != BaseIotaConfig.Defaults.LOCAL_SNAPSHOTS_DB_MAX_SIZE) {
                log.warn("Local snapshots with size condition does not work with pruning disabled");
            }
            maxSize = -1;
        }
        this.snapshotProvider = snapshotProvider;
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
        
        // We only take snapshots when depth allowed by pruning delay (min 10k)
        int distance = snapshotProvider.getLatestSnapshot().getIndex() - snapshotProvider.getInitialSnapshot().getIndex();
        return size > maxSize && distance > config.getLocalSnapshotsPruningDelay();
    }

    @Override
    public int getSnapshotStartingMilestone() throws SnapshotException {
        return snapshotProvider.getInitialSnapshot().getIndex() + MILESTONES;
    }
}
