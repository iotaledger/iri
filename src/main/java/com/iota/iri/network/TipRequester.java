package com.iota.iri.network;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.network.pipeline.TxPipeline;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.thread.ThreadIdentifier;
import com.iota.iri.utils.thread.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TipRequester {

    private static final Logger log = LoggerFactory.getLogger(TipRequester.class);

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final ThreadIdentifier tipRequesterThreadIdentifier = new ThreadIdentifier("Tip Requester");

    private NeighborRouter neighborRouter;
    private Tangle tangle;
    private TransactionRequester txRequester;
    private LatestMilestoneTracker latestMilestoneTracker;

    public void init(NeighborRouter neighborRouter, Tangle tangle, LatestMilestoneTracker latestMilestoneTracker,
            TransactionRequester txRequester) {
        this.neighborRouter = neighborRouter;
        this.tangle = tangle;
        this.latestMilestoneTracker = latestMilestoneTracker;
        this.txRequester = txRequester;
    }

    public void start() {
        ThreadUtils.spawnThread(this::run, tipRequesterThreadIdentifier);
    }

    public void run() {
        log.info("tips requester ready");

        long lastTime = 0;
        while (!shutdown.get()) {
            try {
                final TransactionViewModel msTVM = TransactionViewModel.fromHash(tangle,
                        latestMilestoneTracker.getLatestMilestoneHash());

                if (msTVM.getBytes().length > 0) {
                    for (Neighbor neighbor : neighborRouter.getConnectedNeighbors().values()) {
                        try {
                            neighborRouter.gossipTransactionTo(neighbor, msTVM, true);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                long now = System.currentTimeMillis();
                if ((now - lastTime) > 10000L) {
                    lastTime = now;
                    TxPipeline txPipeline = neighborRouter.getTxPipeline();
                    log.info(
                            "toProcess = {} , toBroadcast = {} , toRequest = {} , toReply = {} / totalTransactions = {}",
                            txPipeline.getReceivedStageQueue().size(), txPipeline.getBroadcastStageQueue().size(),
                            txRequester.numberOfTransactionsToRequest(), txPipeline.getReplyStageQueue().size(),
                            TransactionViewModel.getNumberOfStoredTransactions(tangle));
                }

                Thread.sleep(TimeUnit.SECONDS.toMillis(5));
            } catch (final Exception e) {
                log.error("Tips Requester Thread Exception:", e);
            }
        }
        log.info("tips requester stopped");
    }

    public void shutdown(){
        shutdown.set(true);
        ThreadUtils.stopThread(tipRequesterThreadIdentifier);
    }
}
