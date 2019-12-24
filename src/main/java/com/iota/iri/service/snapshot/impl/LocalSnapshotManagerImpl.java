package com.iota.iri.service.snapshot.impl;

import com.google.common.annotations.VisibleForTesting;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.snapshot.LocalSnapshotManager;
import com.iota.iri.service.snapshot.SnapshotService;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.transactionpruning.TransactionPruner;

import com.iota.iri.utils.thread.ThreadIdentifier;
import com.iota.iri.utils.thread.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Creates a manager for the local snapshots, that takes care of automatically creating local snapshots when the defined
 * intervals have passed.
 * </p>
 * <p>
 * It incorporates a background worker that periodically checks if a new snapshot is due (see {@link
 * #start(MilestoneSolidifier)} and {@link #shutdown()}).
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
     * thread for this instance even when we call the {@link #start(MilestoneSolidifier)} method multiple times.
     */
    private ThreadIdentifier monitorThreadIdentifier = new ThreadIdentifier("Local Snapshots Monitor");

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
    public void start(MilestoneSolidifier milestoneSolidifier) {
        ThreadUtils.spawnThread(() -> monitorThread(milestoneSolidifier), monitorThreadIdentifier);
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
     * {@link SnapshotService#takeLocalSnapshot(MilestoneSolidifier, TransactionPruner)}.
     *
     * @param milestoneSolidifier tracker for the milestones to determine when a new local snapshot is due
     */
    @VisibleForTesting
    void monitorThread(MilestoneSolidifier milestoneSolidifier) {
        while (!Thread.currentThread().isInterrupted()) {
            int localSnapshotInterval = getSnapshotInterval(isInSync(milestoneSolidifier));

            int latestSnapshotIndex = snapshotProvider.getLatestSnapshot().getIndex();
            int initialSnapshotIndex = snapshotProvider.getInitialSnapshot().getIndex();

            if (latestSnapshotIndex - initialSnapshotIndex > config.getLocalSnapshotsDepth() + localSnapshotInterval) {
                try {
                    snapshotService.takeLocalSnapshot(milestoneSolidifier, transactionPruner);
                } catch (SnapshotException e) {
                    log.error("error while taking local snapshot", e);
                }
            }

            ThreadUtils.sleep(LOCAL_SNAPSHOT_RESCAN_INTERVAL);
        }
    }
    
    /**
     * A snapshot is taken in an interval. 
     * This interval changes based on the state of the node.
     * 
     * @param inSync if this node is in sync
     * @return the current interval in which we take local snapshots
     */
    @VisibleForTesting
    int getSnapshotInterval(boolean inSync) {
        return inSync
                ? config.getLocalSnapshotsIntervalSynced()
                : config.getLocalSnapshotsIntervalUnsynced();
    }

    /**
     * A node is defined in sync when the latest snapshot milestone index and the
     * latest milestone index are equal. In order to prevent a bounce between in and
     * out of sync, a buffer is added when a node became in sync.
     * 
     * This will always return false if we are not done scanning milestone
     * candidates during initialization.
     * 
     * @param milestoneSolidifier tracker we use to determine milestones
     * @return <code>true</code> if we are in sync, otherwise <code>false</code>
     */
    @VisibleForTesting
    boolean isInSync(MilestoneSolidifier milestoneSolidifier) {
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
