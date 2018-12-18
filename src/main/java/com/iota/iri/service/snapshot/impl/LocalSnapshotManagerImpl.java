package com.iota.iri.service.snapshot.impl;

import com.iota.iri.validator.MilestoneTracker;
import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.service.snapshot.LocalSnapshotManager;
import com.iota.iri.service.snapshot.SnapshotService;
import com.iota.iri.service.snapshot.SnapshotException;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.transactionpruning.TransactionPruner;
import com.iota.iri.storage.Tangle;

import com.iota.iri.utils.thread.ThreadIdentifier;
import com.iota.iri.utils.thread.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.iota.iri.validator.MilestoneTracker.Status.INITIALIZED;

/**
 * Implements the basic contract of the {@link LocalSnapshotManager}.
 */
public class LocalSnapshotManagerImpl implements LocalSnapshotManager {
    /**
     * The interval (in milliseconds) in which we check if a new local {@link com.iota.iri.service.snapshot.Snapshot} is
     * due.
     */
    private static final int LOCAL_SNAPSHOT_RESCAN_INTERVAL = 10000;

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
     * Tangle object which acts as a database interface.
     */
    private final Tangle tangle;

    /**
     * Configuration with important snapshot related parameters.
     */
    private final SnapshotConfig config;

    /**
     * Holds a reference to the {@link ThreadIdentifier} for the monitor thread.
     *
     * Using a {@link ThreadIdentifier} for spawning the thread allows the {@link ThreadUtils} to spawn exactly one
     * thread for this instance even when we call the {@link #start(MilestoneTracker)} method multiple times.
     */
    private ThreadIdentifier monitorThreadIdentifier = new ThreadIdentifier("Local Snapshots Monitor");

    /**
     * Creates the {@link LocalSnapshotManager} that takes care of automatically creating local snapshots when the
     * defined intervals have passed.
     *
     * It simply stores the passed in parameters in their private properties.
     *
     * @param snapshotProvider data provider for the relevant {@link com.iota.iri.service.snapshot.Snapshot} instances
     * @param transactionPruner manager for the pruning jobs that allows us to clean up old transactions
     * @param tangle object which acts as a database interface
     * @param config configuration with important snapshot related parameters
     */
    public LocalSnapshotManagerImpl(SnapshotProvider snapshotProvider, SnapshotService snapshotService,
            TransactionPruner transactionPruner, Tangle tangle, SnapshotConfig config) {

        this.snapshotProvider = snapshotProvider;
        this.snapshotService = snapshotService;
        this.transactionPruner = transactionPruner;
        this.tangle = tangle;
        this.config = config;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(MilestoneTracker milestoneTracker) {
        ThreadUtils.spawnThread(() -> monitorThread(milestoneTracker), monitorThreadIdentifier);
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
     * {@link SnapshotService#takeLocalSnapshot(Tangle, SnapshotProvider, SnapshotConfig, MilestoneTracker,
     * TransactionPruner)}.
     *
     * @param milestoneTracker tracker for the milestones to determine when a new local snapshot is due
     */
    private void monitorThread(MilestoneTracker milestoneTracker) {
        while (!Thread.currentThread().isInterrupted()) {
            int localSnapshotInterval = milestoneTracker.getStatus() == INITIALIZED &&
                    snapshotProvider.getLatestSnapshot().getIndex() == milestoneTracker.latestMilestoneIndex
                    ? config.getLocalSnapshotsIntervalSynced()
                    : config.getLocalSnapshotsIntervalUnsynced();

            int latestSnapshotIndex = snapshotProvider.getLatestSnapshot().getIndex();
            int initialSnapshotIndex = snapshotProvider.getInitialSnapshot().getIndex();

            if (latestSnapshotIndex - initialSnapshotIndex > config.getLocalSnapshotsDepth() + localSnapshotInterval) {
                try {
                    snapshotService.takeLocalSnapshot(tangle, snapshotProvider, config, milestoneTracker,
                            transactionPruner);
                } catch (SnapshotException e) {
                    log.error("error while taking local snapshot", e);
                }
            }

            ThreadUtils.sleep(LOCAL_SNAPSHOT_RESCAN_INTERVAL);
        }
    }
}
