package com.iota.iri.network.pipeline;

import com.iota.iri.TransactionValidator;
import com.iota.iri.conf.NodeConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.crypto.batched.BatchedHasher;
import com.iota.iri.crypto.batched.BatchedHasherFactory;
import com.iota.iri.crypto.batched.HashRequest;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.network.FIFOCache;
import com.iota.iri.network.NeighborRouter;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.service.milestone.LatestMilestoneTracker;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The {@link TransactionProcessingPipeline} processes transactions which either came from {@link Neighbor} instances or
 * were submitted via {@link com.iota.iri.service.API#broadcastTransactionsStatement(List)}.<br/>
 * The pipeline splits the processing of transactions into different stages which run concurrently:
 * <ul>
 * <li><strong>PreProcess</strong>: expands transaction payloads, computes the digest of transactions received by
 * {@link Neighbor} instances and converts the transaction payload to its trits representation.<br/>
 * Submits to the hashing stage if the transaction payload is not known or to the reply stage if already known.</li>
 * <li><strong>Hashing</strong>: hashes transaction trits using a {@link BatchedHasher} and then submits it further to
 * the validation stage.</li>
 * <li><strong>Validation</strong>: validates the newly received transaction payload and adds it to the known bytes
 * cache.<br/>
 * If the transaction originated from a {@link com.iota.iri.service.API#broadcastTransactionsStatement(List)}, then the
 * transaction is submitted to the received stage, otherwise it is both submitted to the reply and received stage.</li>
 * <li><strong>Reply</strong>: replies to the given neighbor with the requested transaction or a random tip.</li>
 * <li><strong>Received</strong>: stores the newly received and validated transaction and then submits it to the
 * broadcast stage.</li>
 * <li><strong>Broadcast</strong>: broadcasts the given transaction to all connected {@link Neighbor} instances except
 * the neighbor from which the transaction originated from.</li>
 * </ul>
 */
public class TransactionProcessingPipeline {

    private static final Logger log = LoggerFactory.getLogger(TransactionProcessingPipeline.class);
    private ExecutorService stagesThreadPool = Executors.newFixedThreadPool(6);

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    // stages of the protocol protocol
    private PreProcessStage preProcessStage;
    private ReceivedStage receivedStage;
    private ValidationStage validationStage;
    private ReplyStage replyStage;
    private BroadcastStage broadcastStage;
    private BatchedHasher batchedHasher;
    private HashingStage hashingStage;

    private BlockingQueue<ProcessingContext> preProcessStageQueue = new ArrayBlockingQueue<>(100);
    private BlockingQueue<ProcessingContext> validationStageQueue = new ArrayBlockingQueue<>(100);
    private BlockingQueue<ProcessingContext> receivedStageQueue = new ArrayBlockingQueue<>(100);
    private BlockingQueue<ProcessingContext> broadcastStageQueue = new ArrayBlockingQueue<>(100);
    private BlockingQueue<ProcessingContext> replyStageQueue = new ArrayBlockingQueue<>(100);

    /**
     * Defines the different stages of the {@link TransactionProcessingPipeline}.
     */
    public enum Stage {
        PRE_PROCESS, HASHING, VALIDATION, REPLY, RECEIVED, BROADCAST, MULTIPLE, ABORT, FINISH,
    }

    /**
     * Initializes the dependencies of the {@link TransactionProcessingPipeline}.
     * 
     * @param neighborRouter         The {@link NeighborRouter} to use for broadcasting transactions
     * @param config                 The config to set cache sizes and other options
     * @param txValidator            The transaction validator to validate incoming transactions with
     * @param tangle                 The {@link Tangle} database to use to store and load transactions.
     * @param snapshotProvider       The {@link SnapshotProvider} to use to store transactions with.
     * @param tipsViewModel          The {@link TipsViewModel} to load tips from in the reply stage
     * @param latestMilestoneTracker The {@link LatestMilestoneTracker} to load the latest milestone hash from in the
     *                               reply stage
     */
    public void init(NeighborRouter neighborRouter, NodeConfig config, TransactionValidator txValidator, Tangle tangle,
            SnapshotProvider snapshotProvider, TipsViewModel tipsViewModel,
            LatestMilestoneTracker latestMilestoneTracker) {
        FIFOCache<Long, Hash> recentlySeenBytesCache = new FIFOCache<>(config.getCacheSizeBytes());
        this.preProcessStage = new PreProcessStage(recentlySeenBytesCache);
        this.replyStage = new ReplyStage(neighborRouter, config, tangle, tipsViewModel, latestMilestoneTracker,
                snapshotProvider, recentlySeenBytesCache);
        this.broadcastStage = new BroadcastStage(neighborRouter);
        this.validationStage = new ValidationStage(txValidator, recentlySeenBytesCache);
        this.receivedStage = new ReceivedStage(tangle, txValidator, snapshotProvider);
        this.batchedHasher = BatchedHasherFactory.create(BatchedHasherFactory.Type.BCTCURL81, 20);
        this.hashingStage = new HashingStage(batchedHasher);
    }

    /**
     * Kicks of the pipeline by assembling the pipeline and starting all threads.
     */
    public void start() {
        stagesThreadPool.submit(batchedHasher);
        addStage("pre-process", preProcessStageQueue, preProcessStage);
        addStage("validation", validationStageQueue, validationStage);
        addStage("reply", replyStageQueue, replyStage);
        addStage("received", receivedStageQueue, receivedStage);
        addStage("broadcast", broadcastStageQueue, broadcastStage);
    }

    /**
     * Adds the given stage to the processing pipeline.
     * 
     * @param name  the name of the stage
     * @param queue the queue from which contexts are taken to process within the stage
     * @param stage the stage with the processing logic
     */
    private void addStage(String name, BlockingQueue<ProcessingContext> queue,
            com.iota.iri.network.pipeline.Stage stage) {
        stagesThreadPool.submit(new Thread(() -> {
            try {
                while (!shutdown.get()) {
                    ProcessingContext ctx = stage.process(queue.take());
                    switch (ctx.getNextStage()) {
                        case REPLY:
                            replyStageQueue.put(ctx);
                            break;
                        case HASHING:
                            hashAndValidate(ctx);
                            break;
                        case RECEIVED:
                            receivedStageQueue.put(ctx);
                            break;
                        case MULTIPLE:
                            MultiStagePayload payload = (MultiStagePayload) ctx.getPayload();
                            replyStageQueue.put(payload.getLeft());
                            receivedStageQueue.put(payload.getRight());
                            break;
                        case BROADCAST:
                            broadcastStageQueue.put(ctx);
                            break;
                        case ABORT:
                            break;
                        case FINISH:
                            break;
                    }
                }
            } catch (InterruptedException e) {

            } finally {
                log.info("{}-stage shutdown", name);
            }
        }, String.format("%s-stage", name)));
    }

    /**
     * Gets the received stage queue.
     * 
     * @return the received stage queue
     */
    public BlockingQueue<ProcessingContext> getReceivedStageQueue() {
        return receivedStageQueue;
    }

    /**
     * Gets the broadcast stage queue.
     * 
     * @return the broadcast stage queue.
     */
    public BlockingQueue<ProcessingContext> getBroadcastStageQueue() {
        return broadcastStageQueue;
    }

    /**
     * Gets the reply stage queue.
     * 
     * @return the reply stage queue
     */
    public BlockingQueue<ProcessingContext> getReplyStageQueue() {
        return replyStageQueue;
    }

    /**
     * Gets the validation stage queue.
     * 
     * @return the validation stage queue
     */
    public BlockingQueue<ProcessingContext> getValidationStageQueue() {
        return validationStageQueue;
    }

    /**
     * Submits the given data from the given neighbor into the pre processing stage of the pipeline.
     * 
     * @param neighbor the {@link Neighbor} from which the data originated from
     * @param data     the data to process
     */
    public void process(Neighbor neighbor, ByteBuffer data) {
        try {
            preProcessStageQueue.put(new ProcessingContext(new PreProcessPayload(neighbor, data)));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Submits the given transactions trits into the hashing stage of the pipeline.
     * 
     * @param txTrits the transaction trits
     */
    public void process(byte[] txTrits) {
        byte[] txBytes = new byte[Transaction.SIZE];
        Converter.bytes(txTrits, txBytes);
        long txDigest = NeighborRouter.getTxCacheDigest(txBytes);
        HashingPayload payload = new HashingPayload(null, txTrits, txDigest, null);
        hashAndValidate(new ProcessingContext(payload));
    }

    /**
     * Sets up the given hashing stage {@link ProcessingContext} so that up on success, it will submit further to the
     * validation stage.
     * 
     * @param ctx the hashing stage {@link ProcessingContext}
     */
    private void hashAndValidate(ProcessingContext ctx) {
        // the hashing already runs in its own thread,
        // the callback will submit the data to the validation stage
        HashingPayload hashingStagePayload = (HashingPayload) ctx.getPayload();
        hashingStagePayload.setHashRequest(new HashRequest(hashingStagePayload.getTxTrits(), hashTrits -> {
            try {
                hashingStagePayload.setHashTrits(hashTrits);
                // the validation stage takes care of submitting a payload to the reply stage.
                ctx.setNextStage(TransactionProcessingPipeline.Stage.VALIDATION);
                validationStageQueue.put(ctx);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
        hashingStage.process(ctx);
    }

    /**
     * Shut downs the pipeline by shutting down all stages.
     */
    public void shutdown() {
        shutdown.set(true);
        stagesThreadPool.shutdownNow();
    }

    /**
     * Sets the pre process stage. This method should only be used for injecting mocked objects.
     * 
     * @param preProcessStage the {@link PreProcessStage} to use
     */
    public void setPreProcessStage(PreProcessStage preProcessStage) {
        this.preProcessStage = preProcessStage;
    }

    /**
     * Sets the validation stage. This method should only be used for injecting mocked objects.
     * 
     * @param receivedStage the {@link ReceivedStage} to use
     */
    public void setReceivedStage(ReceivedStage receivedStage) {
        this.receivedStage = receivedStage;
    }

    /**
     * Sets the validation stage. This method should only be used for injecting mocked objects.
     *
     * @param validationStage the {@link ValidationStage} to use
     */
    public void setValidationStage(ValidationStage validationStage) {
        this.validationStage = validationStage;
    }

    /**
     * Sets the reply stage. This method should only be used for injecting mocked objects.
     *
     * @param replyStage the {@link ReplyStage} to use
     */
    public void setReplyStage(ReplyStage replyStage) {
        this.replyStage = replyStage;
    }

    /**
     * Sets the broadcast stage. This method should only be used for injecting mocked objects.
     *
     * @param broadcastStage the {@link BroadcastStage} to use
     */
    public void setBroadcastStage(BroadcastStage broadcastStage) {
        this.broadcastStage = broadcastStage;
    }

    /**
     * Sets the hashing stage. This method should only be used for injecting mocked objects.
     *
     * @param hashingStage the {@link HashingStage} to use
     */
    public void setHashingStage(HashingStage hashingStage) {
        this.hashingStage = hashingStage;
    }
}
