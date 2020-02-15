package com.iota.iri.network.impl;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.NeighborRouter;
import com.iota.iri.network.TipsRequester;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.network.pipeline.TransactionProcessingPipeline;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.thread.DedicatedScheduledExecutorService;
import com.iota.iri.utils.thread.SilentScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * The {@link TipsRequesterImpl} requests tips from all neighbors in a given interval.
 */
public class TipsRequesterImpl implements TipsRequester {

    private static final Logger log = LoggerFactory.getLogger(TipsRequesterImpl.class);
    private static final int REQUESTER_THREAD_INTERVAL = 5000;

    private final SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Tips Requester", log);

    private final NeighborRouter neighborRouter;
    private final Tangle tangle;
    private final TransactionRequester txRequester;

    private MilestoneSolidifier milestoneSolidifier;
    private long lastIterationTime = 0;

    /**
     * Creates a tips requester.
     *
     * @param neighborRouter         the {@link NeighborRouter} to use
     * @param tangle                 the {@link Tangle} database to load the latest milestone from
     * @param milestoneSolidifier   the {@link MilestoneSolidifier} to gets the latest milestone hash from
     * @param txRequester            the {@link TransactionRequester} to get the currently number of requested
     *                               transactions from
     */
    public TipsRequesterImpl(NeighborRouter neighborRouter, Tangle tangle, MilestoneSolidifier milestoneSolidifier,
                             TransactionRequester txRequester) {
        this.neighborRouter = neighborRouter;
        this.tangle = tangle;
        this.txRequester = txRequester;
        this.milestoneSolidifier = milestoneSolidifier;
    }

    /**
     * Starts a dedicated thread for the {@link TipsRequesterImpl} and then starts requesting of tips.
     */
    public void start() {
        executorService.silentScheduleWithFixedDelay(this::requestTips, 0, REQUESTER_THREAD_INTERVAL,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Starts the loop to indefinitely request tips from neighbors.
     */
    public void requestTips() {
        try {
            final TransactionViewModel msTVM = TransactionViewModel.fromHash(tangle,
                    milestoneSolidifier.getLatestMilestoneHash());

            if (msTVM.getBytes().length > 0) {
                for (Neighbor neighbor : neighborRouter.getConnectedNeighbors().values()) {
                    if(Thread.currentThread().isInterrupted()){
                        return;
                    }
                    try {
                        neighborRouter.gossipTransactionTo(neighbor, msTVM, true);
                    } catch (Exception e) {
                        log.error("error while sending tip request to neighbor {}. reason: {}", neighbor.getHostAddressAndPort(), e.getMessage());
                    }
                }
            }

            long now = System.currentTimeMillis();
            if ((now - lastIterationTime) > 10_000L) {
                lastIterationTime = now;
                TransactionProcessingPipeline txPipeline = neighborRouter.getTransactionProcessingPipeline();
                log.info(
                        "toProcess = {} , toBroadcast = {} , toRequest = {} , toReply = {} / totalTransactions = {}",
                        txPipeline.getReceivedStageQueue().size(), txPipeline.getBroadcastStageQueue().size(),
                        txRequester.numberOfTransactionsToRequest() +
                                txRequester.numberOfRecentlyRequestedTransactions(),
                        txPipeline.getReplyStageQueue().size(),
                        TransactionViewModel.getNumberOfStoredTransactions(tangle));
            }
        } catch (final Exception e) {
            log.error("Tips Requester Thread Exception:", e);
        }
    }

    /**
     * Shut downs the {@link TipsRequesterImpl}.
     */
    public void shutdown() {
        executorService.shutdownNow();
    }
}
