package com.iota.iri.network.pipeline;

import com.iota.iri.conf.NodeConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.network.FIFOCache;
import com.iota.iri.network.NeighborRouter;
import com.iota.iri.network.TransactionCacheDigester;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.network.protocol.Protocol;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;

import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ReplyStage} replies to the neighbor which supplied the given hash of the requested transaction. If a
 * {@link Hash#NULL_HASH} is supplied, then a random tip is replied back to the neighbor. A neighbor indicates to
 * receive a random tip, when the requested transaction hash is the same as the transaction hash of the transaction in
 * the gossip payload.
 */
public class ReplyStage implements Stage {

    private static final Logger log = LoggerFactory.getLogger(ReplyStage.class);

    private NeighborRouter neighborRouter;
    private Tangle tangle;
    private NodeConfig config;
    private TipsViewModel tipsViewModel;
    private LatestMilestoneTracker latestMilestoneTracker;
    private SnapshotProvider snapshotProvider;
    private FIFOCache<Long, Hash> recentlySeenBytesCache;
    private SecureRandom rnd = new SecureRandom();

    /**
     * Creates a new {@link ReplyStage}.
     * 
     * @param neighborRouter         the {@link NeighborRouter} to use to send the requested transaction
     * @param config                 the {@link NodeConfig}
     * @param tangle                 the {@link Tangle} database to load the request transaction from
     * @param tipsViewModel          the {@link TipsViewModel} to load the random tips from
     * @param latestMilestoneTracker the {@link LatestMilestoneTracker} to load the latest milestone from
     * @param snapshotProvider       the {@link SnapshotProvider} to check the latest solid milestone from
     * @param recentlySeenBytesCache the {@link FIFOCache} to use to cache the replied transaction
     * @param rnd                    the {@link SecureRandom} used to get random values to randomize chances for not
     *                               replying at all or not requesting a not stored requested transaction from neighbors
     */
    public ReplyStage(NeighborRouter neighborRouter, NodeConfig config, Tangle tangle, TipsViewModel tipsViewModel,
            LatestMilestoneTracker latestMilestoneTracker, SnapshotProvider snapshotProvider,
            FIFOCache<Long, Hash> recentlySeenBytesCache, SecureRandom rnd) {
        this.neighborRouter = neighborRouter;
        this.config = config;
        this.tangle = tangle;
        this.tipsViewModel = tipsViewModel;
        this.latestMilestoneTracker = latestMilestoneTracker;
        this.snapshotProvider = snapshotProvider;
        this.recentlySeenBytesCache = recentlySeenBytesCache;
        this.rnd = rnd;
    }

    /**
     * Creates a new {@link ReplyStage}.
     *
     * @param neighborRouter         the {@link NeighborRouter} to use to send the requested transaction
     * @param config                 the {@link NodeConfig}
     * @param tangle                 the {@link Tangle} database to load the request transaction from
     * @param tipsViewModel          the {@link TipsViewModel} to load the random tips from
     * @param latestMilestoneTracker the {@link LatestMilestoneTracker} to load the latest milestone from
     * @param snapshotProvider       the {@link SnapshotProvider} to check the latest solid milestone from
     * @param recentlySeenBytesCache the {@link FIFOCache} to use to cache the replied transaction
     */
    public ReplyStage(NeighborRouter neighborRouter, NodeConfig config, Tangle tangle, TipsViewModel tipsViewModel,
            LatestMilestoneTracker latestMilestoneTracker, SnapshotProvider snapshotProvider,
            FIFOCache<Long, Hash> recentlySeenBytesCache) {
        this.neighborRouter = neighborRouter;
        this.config = config;
        this.tangle = tangle;
        this.tipsViewModel = tipsViewModel;
        this.latestMilestoneTracker = latestMilestoneTracker;
        this.snapshotProvider = snapshotProvider;
        this.recentlySeenBytesCache = recentlySeenBytesCache;
    }

    /**
     * Loads the requested transaction from the database and replies it back to the neighbor who requested it. If the
     * {@link Hash#NULL_HASH} is supplied, then a random tip is replied with.
     * 
     * @param ctx the reply stage {@link ProcessingContext}
     * @return the same {@link ProcessingContext} as passed in
     */
    @Override
    public ProcessingContext process(ProcessingContext ctx) {
        ReplyPayload payload = (ReplyPayload) ctx.getPayload();
        Neighbor neighbor = payload.getOriginNeighbor();
        Hash hashOfRequestedTx = payload.getHashOfRequestedTx();

        TransactionViewModel tvm = null;

        if (hashOfRequestedTx.equals(Hash.NULL_HASH)) {
            try {
                // don't reply to random tip requests if we are synchronized with a max delta of one
                // to the newest milestone
                if (snapshotProvider.getLatestSnapshot().getIndex() >= latestMilestoneTracker.getLatestMilestoneIndex()
                        - 1) {
                    ctx.setNextStage(TransactionProcessingPipeline.Stage.FINISH);
                    return ctx;
                }
                // retrieve random tx
                neighbor.getMetrics().incrRandomTransactionRequestsCount();
                Hash transactionPointer = getRandomTipPointer();
                tvm = TransactionViewModel.fromHash(tangle, transactionPointer);
            } catch (Exception e) {
                log.error("error loading random tip for reply", e);
                ctx.setNextStage(TransactionProcessingPipeline.Stage.ABORT);
                return ctx;
            }
        } else {
            try {
                // retrieve requested tx
                tvm = TransactionViewModel.fromHash(tangle, HashFactory.TRANSACTION.create(hashOfRequestedTx.bytes(), 0,
                        Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES_LENGTH));
            } catch (Exception e) {
                log.error("error while searching for explicitly asked for tx", e);
                ctx.setNextStage(TransactionProcessingPipeline.Stage.ABORT);
                return ctx;
            }
        }

        if (tvm != null && tvm.getType() == TransactionViewModel.FILLED_SLOT) {
            try {
                // send the requested tx data to the requester
                neighborRouter.gossipTransactionTo(neighbor, tvm);
                // cache the replied with tx
                long txDigest = TransactionCacheDigester.getDigest(tvm.getBytes());
                recentlySeenBytesCache.put(txDigest, tvm.getHash());
            } catch (Exception e) {
                log.error("error adding reply tx to neighbor's send queue", e);
            }
            ctx.setNextStage(TransactionProcessingPipeline.Stage.ABORT);
            return ctx;
        }

        // we didn't have the requested transaction (random or explicit) from the neighbor but we will immediately reply
        // with the latest known milestone and a needed transaction hash, to keep up the ping-pong
        try {
            final TransactionViewModel msTVM = TransactionViewModel.fromHash(tangle,
                    latestMilestoneTracker.getLatestMilestoneHash());
            neighborRouter.gossipTransactionTo(neighbor, msTVM, false);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ctx.setNextStage(TransactionProcessingPipeline.Stage.FINISH);
        return ctx;
    }

    private Hash getRandomTipPointer() {
        Hash tip = rnd.nextDouble() < config.getpSendMilestone() ? latestMilestoneTracker.getLatestMilestoneHash()
                : tipsViewModel.getRandomSolidTipHash();
        return tip == null ? Hash.NULL_HASH : tip;
    }
}
