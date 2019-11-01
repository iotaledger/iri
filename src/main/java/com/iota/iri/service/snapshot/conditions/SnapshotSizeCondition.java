package com.iota.iri.service.snapshot.conditions;

import com.iota.iri.conf.DbConfig;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.IotaUtils;

public class SnapshotSizeCondition implements SnapshotCondition {
    
    private final double maxSize;
    private final Tangle tangle;

    public SnapshotSizeCondition(Tangle tangle, DbConfig config, SnapshotProvider snapshotProvider) {
        this.tangle = tangle;
        maxSize = IotaUtils.parseFileSize(config.getDbMaxSize());
    }

    @Override
    public boolean shouldTakeSnapshot(boolean isInSync) {
        return maxSize > 0 && tangle.getPersistanceSize() > maxSize;
    }

    @Override
    public int getSnapshotStartingMilestone() throws SnapshotException {
        return 1;
    }
}
