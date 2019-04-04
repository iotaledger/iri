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

    public ProcessingContext process(ProcessingContext ctx) {
        ReplyPayload payload = (ReplyPayload) ctx.getPayload();
        Neighbor neighbor = payload.getNeighbor();
        Hash hashOfRequestedTx = payload.getHashOfRequestedTx();

        TransactionViewModel tvm = null;

        if (hashOfRequestedTx.equals(Hash.NULL_HASH)) {
            try {
                // retrieve random tx
                if (txRequester.numberOfTransactionsToRequest() == 0
                        || rnd.nextDouble() >= config.getpReplyRandomTip()) {
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
                        Protocol.GOSSIP_REQUESTED_TX_HASH_BYTES));
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
