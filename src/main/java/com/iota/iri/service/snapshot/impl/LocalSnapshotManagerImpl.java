package com.iota.iri.service.snapshot.impl;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.service.snapshot.LocalSnapshotManager;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotCondition;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.snapshot.SnapshotService;
import com.iota.iri.service.transactionpruning.PruningCondition;
import com.iota.iri.service.transactionpruning.TransactionPruner;
import com.iota.iri.service.transactionpruning.TransactionPruningException;
import com.iota.iri.utils.thread.ThreadIdentifier;
import com.iota.iri.utils.thread.ThreadUtils;

import com.google.common.annotations.VisibleForTesting;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Creates a manager for the local snapshots, that takes care of automatically creating local snapshots when the defined
 * intervals have passed.
 * </p>
 * <p>
 * It incorporates a background worker that periodically checks if a new snapshot is due (see {@link
 * #start(LatestMilestoneTracker)} and {@link #shutdown()}).
 * </p>
 */
public class LocalSnapshotManagerImpl implements LocalSnapshotManager {
    /**
     * The interval (in milliseconds) in which we check if a new local {@link com.iota.iri.service.snapshot.Snapshot} is
     * due.
     */
    private static final int LOCAL_SNAPSHOT_RESCAN_INTERVAL = 10000;
    
    /**
     * To prevent jumping back and forth in and out of sync, there is a buffer in between.
     * Only when the latest milestone and latest snapshot differ more than this number, we fall out of sync
     */
    @VisibleForTesting
    static final int LOCAL_SNAPSHOT_SYNC_BUFFER = 5;

    /**
     * Logger for this class allowing us to dump debug and status messages.
     */
    private static final Logger log = LoggerFactory.getLogger(LocalSnapshotManagerImpl.class);

    /**
     * Data provider for the relevant {@link com.iota.iri.service.snapshot.Snapshot} instances.
     */
    private final SnapshotProvider snapshotProvider;

    /**
     * Service that contains the logic for generating local {@link com.iota.iri.service.snapshot.Snapshot}s.
     */
    private final SnapshotService snapshotService;

    /**
     * Manager for the pruning jobs that allows us to clean up old transactions.
     */
    private final TransactionPruner transactionPruner;

    /**
     * Configuration with important snapshot related parameters.
     */
    private final SnapshotConfig config;
    
    /**
     * If this node is currently seen as in sync
     */
    private boolean isInSync;

    /**
     * Holds a reference to the {@link ThreadIdentifier} for the monitor thread.
     *
     * Using a {@link ThreadIdentifier} for spawning the thread allows the {@link ThreadUtils} to spawn exactly one
     * thread for this instance even when we call the {@link #start(LatestMilestoneTracker)} method multiple times.
     */
    private ThreadIdentifier monitorThreadIdentifier = new ThreadIdentifier("Local Snapshots Monitor");

    private SnapshotCondition[] snapshotConditions;
    private PruningCondition[] pruningConditions;

    /**
     * @param snapshotProvider data provider for the snapshots that are relevant for the node
     * @param snapshotService service instance of the snapshot package that gives us access to packages' business logic
     * @param transactionPruner manager for the pruning jobs that allows us to clean up old transactions
     * @param config important snapshot related configuration parameters
     */
    public LocalSnapshotManagerImpl(SnapshotProvider snapshotProvider, SnapshotService snapshotService,
            TransactionPruner transactionPruner, SnapshotConfig config) {
        this.snapshotProvider = snapshotProvider;
        this.snapshotService = snapshotService;
        this.transactionPruner = transactionPruner;
        this.config = config;
        this.isInSync = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(LatestMilestoneTracker latestMilestoneTracker) {
        ThreadUtils.spawnThread(() -> monitorThread(latestMilestoneTracker), monitorThreadIdentifier);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        ThreadUtils.stopThread(monitorThreadIdentifier);
    }

    /**
     * This method contains the logic for the monitoring Thread.
     *
     * It periodically checks if a new {@link com.iota.iri.service.snapshot.Snapshot} has to be taken until the
     * {@link Thread} is terminated. If it detects that a {@link com.iota.iri.service.snapshot.Snapshot} is due it
     * triggers the creation of the {@link com.iota.iri.service.snapshot.Snapshot} by calling
     * {@link SnapshotService#takeLocalSnapshot}.
     *
     * @param latestMilestoneTracker tracker for the milestones to determine when a new local snapshot is due
     */
    @VisibleForTesting
    void monitorThread(LatestMilestoneTracker latestMilestoneTracker) {
        while (!Thread.currentThread().isInterrupted()) {
            // Possibly takes a snapshot if we can
            handleSnapshot(latestMilestoneTracker);
            
            // Prunes data separate of a snapshot if we made a snapshot of the pruned data now or previously
            if (config.getLocalSnapshotsPruningEnabled()) {
                handlePruning();
            }
            ThreadUtils.sleep(LOCAL_SNAPSHOT_RESCAN_INTERVAL);
        }
    }
    
    private Snapshot handleSnapshot(LatestMilestoneTracker latestMilestoneTracker) {
        boolean isInSync = isInSync(latestMilestoneTracker);
        int lowestSnapshotIndex = calculateLowestSnapshotIndex(isInSync);
        if (canTakeSnapshot(lowestSnapshotIndex, latestMilestoneTracker)) {
            try {
                log.debug("Taking snapshot at index {}", lowestSnapshotIndex);
                return snapshotService.takeLocalSnapshot(
                        latestMilestoneTracker, transactionPruner, lowestSnapshotIndex);
            } catch (SnapshotException e) {
                log.error("error while taking local snapshot", e);
            } catch (Exception e) {
                log.error("could not load the target milestone", e);
            }
        }
        return null;
    }
    
    /**
     * Calculates the oldest milestone index allowed by all snapshotConditions.
     * 
     * @param isInSync If this node is considered in sync, to prevent recalculation.
     * @return The lowest allowed milestone we can snapshot according to the node
     */
    private int calculateLowestSnapshotIndex(boolean isInSync) {
        int lowestSnapshotIndex = -1;
        for (SnapshotCondition condition : snapshotConditions) {
            try {
                if (condition.shouldTakeSnapshot(isInSync) && (lowestSnapshotIndex == -1 
                        || condition.getSnapshotStartingMilestone() < lowestSnapshotIndex)) {
                    
                    lowestSnapshotIndex = condition.getSnapshotStartingMilestone();
                }
            } catch (SnapshotException e) {
                log.error("error while checking local snapshot availabilty", e);
            }
        }
        return lowestSnapshotIndex;
    }
    
    private boolean canTakeSnapshot(int lowestSnapshotIndex, LatestMilestoneTracker latestMilestoneTracker) {
        return lowestSnapshotIndex != -1 
                && latestMilestoneTracker.isInitialScanComplete()
                && lowestSnapshotIndex > snapshotProvider.getInitialSnapshot().getIndex()
                && lowestSnapshotIndex <= snapshotProvider.getLatestSnapshot().getIndex() - config.getLocalSnapshotsDepth();
    }
    
    private void handlePruning() {
        // Recalculate inSync, as a snapshot can take place which takes a while
        try {
            int pruningMilestoneIndex = calculateLowestPruningIndex();
            if (canPrune(pruningMilestoneIndex)) {
                log.info("Pruning at index {}", pruningMilestoneIndex);
                // Pruning will not happen when pruning is turned off, but we don't want to know about that here
                snapshotService.pruneSnapshotData(transactionPruner, pruningMilestoneIndex);
            } else {
                if (pruningMilestoneIndex > 0) {
                    log.debug("Can't prune at index {}", pruningMilestoneIndex);
                }
            }
        } catch (SnapshotException e) {
            log.error("error while pruning", e);
        } catch (Exception e) {
            log.error("could not prune data", e);
        }
    }
    
    /**
     * Calculates the oldest pruning milestone index allowed by all conditions.(
     * If the lowest index violates our set minimum pruning depth, the minimum will be returned instead.
     * 
     * @return The lowest allowed milestone we can prune according to the node, or -1 if we cannot
     * @throws SnapshotException if we could not obtain the requirements for determining the snapshot milestone
     */
    private int calculateLowestPruningIndex() throws TransactionPruningException {
        int lowestPruningIndex = -1;

        for (PruningCondition condition : pruningConditions) {
            int snapshotPruningMilestone = condition.getSnapshotPruningMilestone();
            if (condition.shouldPrune()
                    && (lowestPruningIndex == -1 || snapshotPruningMilestone < lowestPruningIndex)) {
                lowestPruningIndex = snapshotPruningMilestone;
            }
        }

        int localSnapshotIndex = this.snapshotProvider.getInitialSnapshot().getIndex();
        // since depth condition may be skipped we must make sure that we don't prune too shallow
        int maxPruningIndex = localSnapshotIndex - BaseIotaConfig.Defaults.LOCAL_SNAPSHOTS_PRUNING_DELAY_MIN;
        if (lowestPruningIndex != -1 && lowestPruningIndex > maxPruningIndex) {
            log.warn("Attempting to prune at milestone index {}. But it is not at least {} milestones below "
                            + "last local snapshot {}. Pruning at {} instead",
                    lowestPruningIndex, BaseIotaConfig.Defaults.LOCAL_SNAPSHOTS_PRUNING_DELAY_MIN,
                    localSnapshotIndex, maxPruningIndex);
            return maxPruningIndex;
        }
        return lowestPruningIndex;
    }

    private boolean canPrune(int pruningMilestoneIndex) {
        int snapshotIndex = snapshotProvider.getInitialSnapshot().getIndex();
        // -1 means we can't prune, smaller than snapshotIndex because we prune until index + 1
        return pruningMilestoneIndex > 0 && pruningMilestoneIndex < snapshotIndex;
    }

    /**
     * A node is defined in sync when the latest snapshot milestone index and the
     * latest milestone index are equal. In order to prevent a bounce between in and
     * out of sync, a buffer is added when a node became in sync.
     * 
     * This will always return false if we are not done scanning milestone
     * candidates during initialization.
     * 
     * @param latestMilestoneTracker tracker we use to determine milestones
     * @return <code>true</code> if we are in sync, otherwise <code>false</code>
     */
    @VisibleForTesting
    boolean isInSync(LatestMilestoneTracker latestMilestoneTracker) {
        if (!latestMilestoneTracker.isInitialScanComplete()) {
            return false;
        }

        int latestIndex = latestMilestoneTracker.getLatestMilestoneIndex();
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

    @Override
    public void addSnapshotCondition(SnapshotCondition... conditions) {
        if (this.snapshotConditions == null) {
            this.snapshotConditions = conditions.clone();
        } else {
            this.snapshotConditions = ArrayUtils.addAll(this.snapshotConditions, conditions);
        }
    }

    @Override
    public void addPruningConditions(PruningCondition... conditions) {
        if (this.pruningConditions == null) {
            this.pruningConditions = conditions.clone();
        } else {
            this.pruningConditions = ArrayUtils.addAll(this.pruningConditions, conditions);
        }
    }
}
