package com.iota.iri.network.pipeline;

import com.iota.iri.conf.NodeConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.network.FIFOCache;
import com.iota.iri.network.NeighborRouter;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.network.protocol.Protocol;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
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
public class ReplyStage {

    private static final Logger log = LoggerFactory.getLogger(ReplyStage.class);

    private NeighborRouter neighborRouter;
    private Tangle tangle;
    private NodeConfig config;
    private TransactionRequester txRequester;
    private TipsViewModel tipsViewModel;
    private LatestMilestoneTracker latestMilestoneTracker;
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
     * @param recentlySeenBytesCache the {@link FIFOCache} to use to cache the replied transaction
     * @param txRequester            the {@link TransactionRequester} to put in transaction which are requested but not
     *                               available on the node to request them from other neighbors
     * @param rnd                    the {@link SecureRandom} used to get random values to randomize chances for not
     *                               replying at all or not requesting a not stored requested transaction from neighbors
     */
    public ReplyStage(NeighborRouter neighborRouter, NodeConfig config, Tangle tangle, TipsViewModel tipsViewModel,
            LatestMilestoneTracker latestMilestoneTracker, FIFOCache<Long, Hash> recentlySeenBytesCache,
            TransactionRequester txRequester, SecureRandom rnd) {
        this.neighborRouter = neighborRouter;
        this.config = config;
        this.tangle = tangle;
        this.tipsViewModel = tipsViewModel;
        this.latestMilestoneTracker = latestMilestoneTracker;
        this.recentlySeenBytesCache = recentlySeenBytesCache;
        this.txRequester = txRequester;
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
     * @param recentlySeenBytesCache the {@link FIFOCache} to use to cache the replied transaction
     * @param txRequester            the {@link TransactionRequester} to put in transaction which are requested but not
     *                               available on the node to request them from other neighbors
     */
    public ReplyStage(NeighborRouter neighborRouter, NodeConfig config, Tangle tangle, TipsViewModel tipsViewModel,
            LatestMilestoneTracker latestMilestoneTracker, FIFOCache<Long, Hash> recentlySeenBytesCache,
            TransactionRequester txRequester) {
        this.neighborRouter = neighborRouter;
        this.config = config;
        this.tangle = tangle;
        this.tipsViewModel = tipsViewModel;
        this.latestMilestoneTracker = latestMilestoneTracker;
        this.recentlySeenBytesCache = recentlySeenBytesCache;
        this.txRequester = txRequester;
    }

    /**
     * Loads the requested transaction from the database and replies it back to the neighbor who requested it. If the
     * {@link Hash#NULL_HASH} is supplied, then a random tip is replied with.
     * 
     * @param ctx the reply stage {@link ProcessingContext}
     * @return the same {@link ProcessingContext} as passed in
     */
    public ProcessingContext process(ProcessingContext ctx) {
        ReplyPayload payload = (ReplyPayload) ctx.getPayload();
        Neighbor neighbor = payload.getNeighbor();
        Hash hashOfRequestedTx = payload.getHashOfRequestedTx();

        TransactionViewModel tvm = null;

        if (hashOfRequestedTx.equals(Hash.NULL_HASH)) {
            try {
                // retrieve random tx
                if (txRequester.numberOfTransactionsToRequest() == 0
                        && rnd.nextDouble() >= config.getpReplyRandomTip()) {
                    return ctx;
                }
                neighbor.getMetrics().incrRandomTransactionRequestsCount();
                Hash transactionPointer = getRandomTipPointer();
                tvm = TransactionViewModel.fromHash(tangle, transactionPointer);
            } catch (Exception e) {
                log.error("error loading random tip for reply", e);
                return ctx;
            }
        } else {
            try {
                // retrieve requested tx
                tvm = TransactionViewModel.fromHash(tangle, HashFactory.TRANSACTION.create(hashOfRequestedTx.bytes(), 0,
                        Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES_LENGTH));
            } catch (Exception e) {
                log.error("error while searching for explicitly asked for tx", e);
            }
        }

        if (tvm != null && tvm.getType() == TransactionViewModel.FILLED_SLOT) {
            try {
                // send the requested tx data to the requester
                neighborRouter.gossipTransactionTo(neighbor, tvm);
                // cache the replied with tx
                long txDigest = NeighborRouter.getTxCacheDigest(tvm.getBytes());
                recentlySeenBytesCache.put(txDigest, tvm.getHash());
            } catch (Exception e) {
                log.error("error adding reply tx to neighbor's send queue", e);
            }
            return ctx;
        }

        // if we don't have the requested transaction (not a random tip) and the propagation
        // chance gets hit, we put the requested transaction into our own request queue.
        if (hashOfRequestedTx.equals(Hash.NULL_HASH) || rnd.nextDouble() >= config.getpPropagateRequest()) {
            return ctx;
        }

        try {
            // we don't have the requested tx, so we add it to our own request queue
            txRequester.requestTransaction(hashOfRequestedTx, false);
        } catch (Exception e) {
            log.error("error adding requested tx to own request queue", e);
        }
        return ctx;
    }

    private Hash getRandomTipPointer() {
        Hash tip = rnd.nextDouble() < config.getpSendMilestone() ? latestMilestoneTracker.getLatestMilestoneHash()
                : tipsViewModel.getRandomSolidTipHash();
        return tip == null ? Hash.NULL_HASH : tip;
    }
}
