package com.iota.iri.service.snapshot.conditions;

import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.IotaUtils;

public class SnapshotSizeCondition implements SnapshotCondition {
    
    private final double maxSize;
    private final Tangle tangle;

    public SnapshotSizeCondition(Tangle tangle, SnapshotConfig config, SnapshotProvider snapshotProvider) {
        this.tangle = tangle;
        maxSize = IotaUtils.parseFileSize(config.getLocalSnapshotsDbMaxSize());
    }

    @Override
    public boolean shouldTakeSnapshot(boolean isInSync) {
        long size = tangle.getPersistanceSize();
        System.out.println("DB size: " + size + "(max: " + maxSize + ")");
        return maxSize > 0 && tangle.getPersistanceSize() > maxSize;
    }

    @Override
    public int getSnapshotStartingMilestone() throws SnapshotException {
        throw new SnapshotException("Not yet implemented");
    }
}
