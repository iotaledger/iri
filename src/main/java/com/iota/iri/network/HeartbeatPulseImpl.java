package com.iota.iri.network;

import com.iota.iri.conf.SnapshotConfig;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.network.protocol.Heartbeat;
import com.iota.iri.service.snapshot.LocalSnapshotManager;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.transactionpruning.TransactionPruningException;
import com.iota.iri.utils.thread.DedicatedScheduledExecutorService;
import com.iota.iri.utils.thread.SilentScheduledExecutorService;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link HeartbeatPulse} interface.
 */
public class HeartbeatPulseImpl implements HeartbeatPulse {

    private NeighborRouter neighborRouter;
    private SnapshotProvider snapshotProvider;
    private SnapshotConfig snapshotConfig;
    private LocalSnapshotManager localSnapshotManager;

    private static final Logger log = LoggerFactory.getLogger(HeartbeatPulseImpl.class);

    /**
     * The rate in milliseconds, at which heartbeats are sent out.
     */
    private static final int HEARTBEAT_RATE_MILLIS = 60000;

    /**
     * Holds a reference to the manager of the background worker.
     */
    private final SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Heartbeat Pulse");

    /**
     * Constructor for heartbeat pulse
     * 
     * @param neighborRouter       Neighbor router
     * @param snapshotProvider     Snapshot provider
     * @param snapshotConfig       Snapshot config
     * @param localSnapshotManager local snapshot manager
     */
    public HeartbeatPulseImpl(NeighborRouter neighborRouter, SnapshotProvider snapshotProvider,
            SnapshotConfig snapshotConfig, LocalSnapshotManager localSnapshotManager) {
        this.neighborRouter = neighborRouter;
        this.snapshotProvider = snapshotProvider;
        this.snapshotConfig = snapshotConfig;
        this.localSnapshotManager = localSnapshotManager;
    }

    @Override
    public void start() {
        executorService.silentScheduleWithFixedDelay(this::sendHeartbeat, 0, HEARTBEAT_RATE_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() {
        executorService.shutdownNow();
    }

    @Override
    public void sendHeartbeat() {
        int lastSolidMilestoneIndex = snapshotProvider.getLatestSnapshot().getIndex();
        int firstSolidMilestoneIndex = snapshotProvider.getInitialSnapshot().getIndex();

        if (snapshotConfig.getLocalSnapshotsPruningEnabled()) {
            try {
                firstSolidMilestoneIndex = localSnapshotManager.maxSnapshotPruningMilestone();
            } catch (TransactionPruningException e) {
                log.info("Failed to get first solid milestone with pruning" + e.getMessage());
                firstSolidMilestoneIndex = snapshotProvider.getInitialSnapshot().getIndex();
            }
        }

        Heartbeat heartbeat = new Heartbeat();
        heartbeat.setFirstSolidMilestoneIndex(firstSolidMilestoneIndex);
        heartbeat.setLastSolidMilestoneIndex(lastSolidMilestoneIndex);

        Map<String, Neighbor> currentlyConnectedNeighbors = neighborRouter.getConnectedNeighbors();
        for (Neighbor neighbor : currentlyConnectedNeighbors.values()) {
            neighborRouter.gossipHeartbeatTo(neighbor, heartbeat);
        }
    }
}
