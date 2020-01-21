package com.iota.iri.service.transactionpruning;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.model.IntegerIndex;
import com.iota.iri.model.persistables.Milestone;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.IotaUtils;
import com.iota.iri.utils.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initiates pruning based on how much space the DB takes on the file system
 */
public class SizePruningCondition implements PruningCondition {
    private static final Logger log = LoggerFactory.getLogger(SizePruningCondition.class);

    /**
     * Percentage margin for the database max size due to
     */
    private static final double MARGIN = 1;

    /**
     * Amount of milestones we prune when the DB is too large
     */
    public static final int MILESTONES = 25;

    /**
     * The maximum size we want the DB to have, with margin due to DB delays for writing to disk
     */
    private final long maxSize;

    /**
     * Database object we use to find the lowest milestone
     */
    private final Tangle tangle;

    /**
     * The cached size of the db, so we don't keep pruning when the size doesn't change before a DB compaction
     */
    private long lastSize = -1;


    /**
     * A condition to prune based on DB size
     *
     * @param tangle the database interface.
     * @param config configuration with snapshot specific settings.
     */
    public SizePruningCondition(Tangle tangle, SnapshotConfig config) {
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

    @Override
    public boolean shouldPrune() {
        if (maxSize <= 0) {
            return false;
        }
        long size = tangle.getPersistanceSize();
        if (size == lastSize) {
            return false;
        }

        lastSize = size;
        return size > maxSize;
    }

    @Override
    public int getSnapshotPruningMilestone() throws TransactionPruningException {
        int initialIndex;
        try {
            Pair<Indexable, Persistable> ms = tangle.getFirst(Milestone.class, IntegerIndex.class);
            if (ms == null || ms.low == null) {
                return -1;
            }
            
            initialIndex = ((IntegerIndex)ms.low).getValue();
        } catch (Exception e) {
            throw new TransactionPruningException("failed to find oldest milestone", e);
        }

        return initialIndex + MILESTONES;
    }
}
