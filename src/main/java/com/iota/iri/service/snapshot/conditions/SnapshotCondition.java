package com.iota.iri.service.snapshot.conditions;

import com.iota.iri.service.snapshot.SnapshotException;

public interface SnapshotCondition {

    public boolean shouldTakeSnapshot(boolean isInSync);
    
    public int getSnapshotStartingMilestone() throws SnapshotException;
}
