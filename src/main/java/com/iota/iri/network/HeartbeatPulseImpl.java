package com.iota.iri.network;

import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.network.protocol.Heartbeat;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.utils.thread.DedicatedScheduledExecutorService;
import com.iota.iri.utils.thread.SilentScheduledExecutorService;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link HeartbeatPulse} interface.
 */
public class HeartbeatPulseImpl implements HeartbeatPulse {

    private NeighborRouter neighborRouter;
    private SnapshotProvider snapshotProvider;

    /**
     * The rate in milliseconds, at which heartbeats are sent out.
     */
    private static final int HEARTBEAT_RATE_MILLIS = 60000;

    /**
     * Holds a reference to the manager of the background worker.
     */
    private final SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Heartbeat Pulse");

    public HeartbeatPulseImpl(NeighborRouter neighborRouter, SnapshotProvider snapshotProvider) {
        this.neighborRouter = neighborRouter;
        this.snapshotProvider = snapshotProvider;
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
        // The first solid milestone in DB w/o pruning.
        int firstSolidMilestoneIndex = 0; // TODO: how do we get this?

        Heartbeat heartbeat = new Heartbeat();
        heartbeat.setFirstSolidMilestoneIndex(firstSolidMilestoneIndex);
        heartbeat.setLastSolidMilestoneIndex(lastSolidMilestoneIndex);

        Map<String, Neighbor> currentlyConnectedNeighbors = neighborRouter.getConnectedNeighbors();
        for (Neighbor neighbor : currentlyConnectedNeighbors.values()) {
            neighborRouter.gossipHeartbeatTo(neighbor, heartbeat);
        }
    }
}
