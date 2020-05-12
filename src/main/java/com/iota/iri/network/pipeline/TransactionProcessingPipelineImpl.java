package com.iota.iri.network.pipeline;

import com.iota.iri.conf.NodeConfig;
import com.iota.iri.controllers.TipsViewModel;
import com.iota.iri.crypto.batched.BatchedHasher;
import com.iota.iri.crypto.batched.BatchedHasherFactory;
import com.iota.iri.crypto.batched.HashRequest;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.network.FIFOCache;
import com.iota.iri.network.NeighborRouter;
import com.iota.iri.network.TransactionCacheDigester;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.network.neighbor.Neighbor;
import com.iota.iri.service.milestone.InSyncService;
import com.iota.iri.service.milestone.MilestoneService;
import com.iota.iri.service.milestone.MilestoneSolidifier;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.validation.TransactionSolidifier;
import com.iota.iri.service.validation.TransactionValidator;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;
import com.iota.iri.utils.IotaUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The {@link TransactionProcessingPipelineImpl} processes transactions which either came from {@link Neighbor} instances or
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
public class TransactionProcessingPipelineImpl implements TransactionProcessingPipeline {

    private static final Logger log = LoggerFactory.getLogger(TransactionProcessingPipelineImpl.class);
    private ExecutorService stagesThreadPool = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    /**
     * List of stages that will be ignored when determining thread count
     */
    private static final List IGNORED_STAGES = IotaUtils.createImmutableList(Stage.MULTIPLE, Stage.ABORT, Stage.FINISH);
    private static final int NUMBER_OF_THREADS = Stage.values().length - IGNORED_STAGES.size();

    // stages of the protocol protocol
    private PreProcessStage preProcessStage;
    private ReceivedStage receivedStage;
    private ValidationStage validationStage;
    private ReplyStage replyStage;
    private BroadcastStage broadcastStage;
    private BatchedHasher batchedHasher;
    private HashingStage hashingStage;
    private SolidifyStage solidifyStage;
    private MilestoneStage milestoneStage;

    private BlockingQueue<ProcessingContext> milestoneStageQueue = new LinkedBlockingQueue<>();
    private BlockingQueue<ProcessingContext> preProcessStageQueue = new LinkedBlockingQueue<>();
    private BlockingQueue<ProcessingContext> validationStageQueue = new LinkedBlockingQueue<>();
    private BlockingQueue<ProcessingContext> receivedStageQueue = new LinkedBlockingQueue<>();
    private BlockingQueue<ProcessingContext> replyStageQueue = new LinkedBlockingQueue<>();
    private BlockingQueue<ProcessingContext> broadcastStageQueue = new LinkedBlockingQueue<>();
    private BlockingQueue<ProcessingContext> solidifyStageQueue = new LinkedBlockingQueue<>();

    /**
     * Creates a {@link TransactionProcessingPipeline}.
     *
     * @param neighborRouter         The {@link NeighborRouter} to use for broadcasting transactions
     * @param config                 The config to set cache sizes and other options
     * @param txValidator            The transaction validator to validate incoming transactions with
     * @param tangle                 The {@link Tangle} database to use to store and load transactions.
     * @param snapshotProvider       The {@link SnapshotProvider} to use to store transactions with.
     * @param tipsViewModel          The {@link TipsViewModel} to load tips from in the reply stage
     * @param inSyncService          The {@link InSyncService} to check if we are in sync
     */
    public TransactionProcessingPipelineImpl(NeighborRouter neighborRouter, NodeConfig config,
            TransactionValidator txValidator, Tangle tangle, SnapshotProvider snapshotProvider,
            TipsViewModel tipsViewModel, MilestoneSolidifier milestoneSolidifier,
            TransactionRequester transactionRequester, TransactionSolidifier txSolidifier,
            MilestoneService milestoneService, InSyncService inSyncService) {
        FIFOCache<Long, Hash> recentlySeenBytesCache = new FIFOCache<>(config.getCacheSizeBytes());
        this.preProcessStage = new PreProcessStage(recentlySeenBytesCache);
        this.replyStage = new ReplyStage(neighborRouter, config, tangle, tipsViewModel, milestoneSolidifier,
                snapshotProvider, recentlySeenBytesCache);
        this.broadcastStage = new BroadcastStage(neighborRouter, txSolidifier, inSyncService);
        this.validationStage = new ValidationStage(txValidator, recentlySeenBytesCache);
        this.receivedStage = new ReceivedStage(tangle, txSolidifier, snapshotProvider, transactionRequester,
                milestoneService, config.getCoordinator());
        this.batchedHasher = BatchedHasherFactory.create(BatchedHasherFactory.Type.BCTCURL81, 20);
        this.hashingStage = new HashingStage(batchedHasher);
        this.solidifyStage = new SolidifyStage(txSolidifier, tipsViewModel, tangle);
        this.milestoneStage = new MilestoneStage(milestoneSolidifier, snapshotProvider, txSolidifier);
    }

    @Override
    public void start() {
        stagesThreadPool.submit(batchedHasher);
        addStage("pre-process", preProcessStageQueue, preProcessStage);
        addStage("validation", validationStageQueue, validationStage);
        addStage("reply", replyStageQueue, replyStage);
        addStage("received", receivedStageQueue, receivedStage);
        addStage("broadcast", broadcastStageQueue, broadcastStage);
        addStage("solidify", solidifyStageQueue, solidifyStage);
        addStage("milestone", milestoneStageQueue, milestoneStage);
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
                while (!Thread.currentThread().isInterrupted()) {
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
                        case SOLIDIFY:
                            solidifyStageQueue.put(ctx);
                            break;
                        case MILESTONE:
                            milestoneStageQueue.put(ctx);
                            break;
                        case ABORT:
                            break;
                        case FINISH:
                            break;
                        default:
                            // do nothing
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                log.info("{}-stage shutdown", name);
            }
        }, String.format("%s-stage", name)));
    }

    @Override
    public BlockingQueue<ProcessingContext> getReceivedStageQueue() {
        return receivedStageQueue;
    }

    @Override
    public BlockingQueue<ProcessingContext> getBroadcastStageQueue() {
        return broadcastStageQueue;
    }

    @Override
    public BlockingQueue<ProcessingContext> getReplyStageQueue() {
        return replyStageQueue;
    }

    @Override
    public BlockingQueue<ProcessingContext> getValidationStageQueue() {
        return validationStageQueue;
    }

    @Override
    public void process(Neighbor neighbor, ByteBuffer data) {
        try {
            preProcessStageQueue.put(new ProcessingContext(new PreProcessPayload(neighbor, data)));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void process(byte[] txTrits) {
        byte[] txBytes = new byte[Transaction.SIZE];
        Converter.bytes(txTrits, txBytes);
        long txDigest = TransactionCacheDigester.getDigest(txBytes);
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
                log.error("unable to put processing context into hashing stage. reason: {}", e.getMessage());
            }
        }));
        hashingStage.process(ctx);
    }

    @Override
    public void shutdown() {
        stagesThreadPool.shutdownNow();
    }

    @Override
    public void setPreProcessStage(PreProcessStage preProcessStage) {
        this.preProcessStage = preProcessStage;
    }

    @Override
    public void setReceivedStage(ReceivedStage receivedStage) {
        this.receivedStage = receivedStage;
    }

    @Override
    public void setValidationStage(ValidationStage validationStage) {
        this.validationStage = validationStage;
    }

    @Override
    public void setReplyStage(ReplyStage replyStage) {
        this.replyStage = replyStage;
    }

    @Override
    public void setBroadcastStage(BroadcastStage broadcastStage) {
        this.broadcastStage = broadcastStage;
    }

    @Override
    public void setHashingStage(HashingStage hashingStage) {
        this.hashingStage = hashingStage;
    }

    @Override
    public void setSolidifyStage(SolidifyStage solidifyStage){
        this.solidifyStage = solidifyStage;
    }

    @Override
    public void setMilestoneStage(MilestoneStage milestoneStage){
        this.milestoneStage = milestoneStage;
    }
}
