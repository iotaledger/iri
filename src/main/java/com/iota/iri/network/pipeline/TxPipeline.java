package com.iota.iri.network.pipeline;

import com.iota.iri.TransactionValidator;
import com.iota.iri.conf.NodeConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.crypto.batched.BatchedHasher;
import com.iota.iri.crypto.batched.BatchedHasherFactory;
import com.iota.iri.crypto.batched.HashRequest;
import com.iota.iri.model.Hash;
import com.iota.iri.network.FIFOCache;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.network.NeighborRouter;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.*;

public class TxPipeline {

    private static final Logger log = LoggerFactory.getLogger(TxPipeline.class);
    private ExecutorService stagesThreadPool = Executors.newFixedThreadPool(6);

    // stages of the protocol protocol
    private PreProcessStage preProcessStage;
    private ReceivedStage receivedStage;
    private ValidationStage validationStage;
    private ReplyStage replyStage;
    private BroadcastStage broadcastStage;
    private BatchedHasher batchedHasher;
    private HashingStage hashingStage;

    private FIFOCache<Long, Hash> recentlySeenBytesCache;
    private BlockingQueue<ProcessingContext<PreProcessPayload>> preProcessStageQueue = new ArrayBlockingQueue<>(100);
    private BlockingQueue<ProcessingContext<? extends ValidationPayload>> validationStageQueue = new ArrayBlockingQueue<>(100);
    private BlockingQueue<ProcessingContext<ReceivedPayload>> receivedStageQueue = new ArrayBlockingQueue<>(100);
    private BlockingQueue<ProcessingContext<BroadcastPayload>> broadcastStageQueue = new ArrayBlockingQueue<>(100);
    private BlockingQueue<ProcessingContext<ReplyPayload>> replyStageQueue = new ArrayBlockingQueue<>(100);

    public enum Stage {
        PRE_PROCESS,
        HASHING,
        VALIDATION,
        REPLY,
        RECEIVED,
        BROADCAST,
        MULTIPLE,
        ABORT
    }

    public void init(NeighborRouter neighborRouter, NodeConfig config, TransactionValidator txValidator, Tangle tangle,
                     SnapshotProvider snapshotProvider, TransactionRequester txRequester,
                     TipsViewModel tipsViewModel, LatestMilestoneTracker latestMilestoneTracker) {
        this.recentlySeenBytesCache = new FIFOCache<>(config.getCacheSizeBytes(), config.getpDropCacheEntry());
        this.preProcessStage = new PreProcessStage(recentlySeenBytesCache);
        this.replyStage = new ReplyStage(neighborRouter, config, tangle, tipsViewModel, latestMilestoneTracker, recentlySeenBytesCache, txRequester);
        this.broadcastStage = new BroadcastStage(neighborRouter);
        this.validationStage = new ValidationStage(txValidator, recentlySeenBytesCache);
        this.receivedStage = new ReceivedStage(tangle, txValidator, snapshotProvider);
        this.batchedHasher = BatchedHasherFactory.create(BatchedHasherFactory.Type.BCTCURL81);
        this.hashingStage = new HashingStage(batchedHasher);
    }

    public void start() {
        /*
            Assembly:
            pre process stage:
                -> reply stage if tx already seen
                -> hashing stage if tx not already seen
            hashing stage:
                -> validation stage
            validation stage:
                -> received stage if not tx not gotten from a neighbor
                -> received/reply stage otherwise
            received stage:
                -> broadcast stage

            external actors:
                neighbors:
                    -> pre process stage
                broadcastTransactions:
                    -> hashing stage
         */
        stagesThreadPool.submit(batchedHasher);
        stagesThreadPool.submit(new Thread(() -> {
            try {
                while (!NeighborRouter.SHUTDOWN.get()) {
                    ProcessingContext ctx = preProcessStageQueue.take();
                    preProcessStage.process(ctx);
                    switch (ctx.getNextStage()) {
                        case REPLY:
                            replyStageQueue.put(ctx);
                            break;
                        case HASHING:
                            hashAndValidate(ctx);
                            break;
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "pre-process-stage"));
        stagesThreadPool.submit(new Thread(() -> {
            try {
                while (!NeighborRouter.SHUTDOWN.get()) {
                    ProcessingContext ctx = validationStageQueue.take();
                    validationStage.process(ctx);
                    switch (ctx.getNextStage()) {
                        case RECEIVED:
                            receivedStageQueue.put(ctx);
                            break;
                        case MULTIPLE:
                            ImmutablePair<ProcessingContext<ReplyPayload>, ProcessingContext<ReceivedPayload>> payload = (ImmutablePair<ProcessingContext<ReplyPayload>, ProcessingContext<ReceivedPayload>>) ctx.getPayload();
                            replyStageQueue.put(payload.getLeft());
                            receivedStageQueue.put(payload.getRight());
                            break;
                        case ABORT:
                            break;
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "tx-validation-stage"));
        stagesThreadPool.submit(new Thread(() -> {
            try {
                while (!NeighborRouter.SHUTDOWN.get()) {
                    ProcessingContext ctx = replyStageQueue.take();
                    replyStage.process(ctx);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "reply-stage"));
        stagesThreadPool.submit(new Thread(() -> {
            try {
                while (!NeighborRouter.SHUTDOWN.get()) {
                    ProcessingContext ctx = receivedStageQueue.take();
                    receivedStage.process(ctx);
                    switch (ctx.getNextStage()) {
                        case BROADCAST:
                            broadcastStageQueue.put(ctx);
                            break;
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "received-stage"));
        stagesThreadPool.submit(new Thread(() -> {
            try {
                while (!NeighborRouter.SHUTDOWN.get()) {
                    ProcessingContext ctx = broadcastStageQueue.take();
                    broadcastStage.process(ctx);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "broadcast-stage"));
    }

    public BlockingQueue<ProcessingContext<ReceivedPayload>> getReceivedStageQueue() {
        return receivedStageQueue;
    }

    public BlockingQueue<ProcessingContext<BroadcastPayload>> getBroadcastStageQueue() {
        return broadcastStageQueue;
    }

    public BlockingQueue<ProcessingContext<ReplyPayload>> getReplyStageQueue() {
        return replyStageQueue;
    }

    public void process(Neighbor neighbor, ByteBuffer data) {
        ProcessingContext<PreProcessPayload> ctx = new ProcessingContext(new PreProcessPayload(neighbor, data));
        try {
            preProcessStageQueue.put(ctx);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void process(byte[] txTrits) {
        HashingPayload payload = new HashingPayload(null, txTrits, null, null);
        hashAndValidate(new ProcessingContext<HashingPayload>(payload));
    }

    private void hashAndValidate(ProcessingContext<HashingPayload> ctx) {
        // the hashing already runs in its own thread,
        // the callback will submit the data to the validation stage
        HashingPayload hashingStagePayload = ctx.getPayload();
        hashingStagePayload.setHashRequest(new HashRequest(hashingStagePayload.getTxTrits(), hashTrits -> {
            try {
                hashingStagePayload.setHashTrits(hashTrits);
                // the validation stage takes care of submitting a payload to the reply stage.
                ctx.setNextStage(TxPipeline.Stage.VALIDATION);
                validationStageQueue.put(ctx);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
        hashingStage.process(ctx);
    }
}
