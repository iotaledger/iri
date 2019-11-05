package com.iota.iri.service.snapshot.conditions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.IotaUtils;

public class SnapshotSizeCondition implements SnapshotCondition {

    private static final Logger log = LoggerFactory.getLogger(SnapshotSizeCondition.class);
    
    private final long maxSize;
    private final Tangle tangle;

    private final SnapshotProvider snapshotProvider;
    
    // As rocksDB only updates the db size every couple minutes, we must cache this value
    // Otherwise we might attempt multiple snapshots when there shouldn't be one happening anymore
    private long lastSize = -1;

    public SnapshotSizeCondition(Tangle tangle, SnapshotConfig config, SnapshotProvider snapshotProvider) {
        this.tangle = tangle;
        maxSize = IotaUtils.parseFileSize(config.getLocalSnapshotsDbMaxSize());
        this.snapshotProvider = snapshotProvider;
    }

    @Override
    public boolean shouldTakeSnapshot(boolean isInSync) {
        long size = tangle.getPersistanceSize();
        log.debug("DB size: " + size + "(max: " + maxSize + ")");
        if (lastSize == size) {
            return false;
        }
        
        return maxSize > 0 && size > maxSize;
    }

    @Override
    public int getSnapshotStartingMilestone() throws SnapshotException {
        lastSize = tangle.getPersistanceSize();
        return snapshotProvider.getInitialSnapshot().getIndex() + 5;
    }
}
