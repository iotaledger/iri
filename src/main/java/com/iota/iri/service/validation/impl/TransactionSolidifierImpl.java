package com.iota.iri.service.validation.impl;

import com.google.common.annotations.VisibleForTesting;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.pipeline.TransactionProcessingPipeline;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.validation.TransactionSolidifier;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.log.interval.IntervalLogger;
import com.iota.iri.utils.thread.DedicatedScheduledExecutorService;
import com.iota.iri.utils.thread.SilentScheduledExecutorService;

import java.util.*;
import java.util.concurrent.*;

import static com.iota.iri.controllers.TransactionViewModel.PREFILLED_SLOT;
import static com.iota.iri.controllers.TransactionViewModel.fromHash;

/**
 * A solidifier class for processing transactions. Transactions are checked for solidity, and missing transactions are
 * subsequently requested. Once a transaction is solidified correctly it is placed into a broadcasting set to be sent to
 * neighboring nodes.
 */
public class TransactionSolidifierImpl implements TransactionSolidifier {

    private Tangle tangle;
    private SnapshotProvider snapshotProvider;
    private TransactionRequester transactionRequester;

    /**
     * Max size for all queues.
     */
    private static final int MAX_SIZE= 10000;

    private static final IntervalLogger log = new IntervalLogger(TransactionSolidifier.class);

    /**
     * Executor service for running the {@link #processTransactionsToSolidify()}.
     */
    private SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Transaction Solidifier", log.delegate());
    /**
     * Interval that the {@link #processTransactionsToSolidify()}  will scan at.
     */
    private static final int RESCAN_INTERVAL = 500;
    /**
     * A queue for processing transactions with the {@link #checkSolidity(Hash)} call. Once a transaction has been
     * marked solid it will be placed into the {@link #transactionsToBroadcast} queue.
     */
    private BlockingQueue<Hash> transactionsToSolidify = new ArrayBlockingQueue<>(MAX_SIZE);

    /**
     * A set of transactions that will be called by the {@link TransactionProcessingPipeline} to be broadcast to
     * neighboring nodes.
     */
    private BlockingQueue<TransactionViewModel> transactionsToBroadcast = new ArrayBlockingQueue<>(MAX_SIZE);


    /**
     * Constructor for the solidifier.
     * @param tangle                    The DB reference
     * @param snapshotProvider          For fetching entry points for solidity checks
     * @param transactionRequester      A requester for missing transactions
     */
    public TransactionSolidifierImpl(Tangle tangle, SnapshotProvider snapshotProvider, TransactionRequester transactionRequester){
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.transactionRequester = transactionRequester;
    }

    /**
     *{@inheritDoc}
     */
    @Override
    public void start(){
        executorService.silentScheduleWithFixedDelay(this::processTransactionsToSolidify, 0, RESCAN_INTERVAL,
                TimeUnit.MILLISECONDS);

    }

    /**
     *{@inheritDoc}
     */
    @Override
    public void shutdown() {
        executorService.shutdownNow();
    }

    /**
     *{@inheritDoc}
     */
    @Override
    public void addToSolidificationQueue(Hash hash){
        try{
            if(transactionsToSolidify.size() >= MAX_SIZE - 1){
                transactionsToSolidify.remove();
            }

            if(!transactionsToSolidify.contains(hash)) {
                    transactionsToSolidify.put(hash);
            }
        } catch(Exception e){
            log.error("Error placing transaction into solidification queue",e);
        }
    }

    @Override
    public boolean addMilestoneToSolidificationQueue(Hash hash, int maxToProcess){
        try{
            TransactionViewModel tx = fromHash(tangle, hash);
            if(tx.isSolid()){
                return true;
            }
            addToSolidificationQueue(hash);
            return false;
        }catch(Exception e){
            log.error("Error adding milestone to solidification queue", e);
            return false;
        }

    }

    /**
     *{@inheritDoc}
     */
    @Override
    public Set<TransactionViewModel> getBroadcastQueue(){
        return new LinkedHashSet<>(transactionsToBroadcast);
    }

    /**
     *{@inheritDoc}
     */
    @Override
    public void clearFromBroadcastQueue(Set<TransactionViewModel> transactionsBroadcasted){
        for (TransactionViewModel tvm : transactionsBroadcasted) {
            transactionsToBroadcast.remove(tvm);
        }
    }


    /**
     * Iterate through the {@link #transactionsToSolidify} queue and call {@link #checkSolidity(Hash)} on each hash.
     * Solid transactions are then processed into the {@link #transactionsToBroadcast} queue.
     */
    private void processTransactionsToSolidify(){
        Hash hash;
        while (!Thread.currentThread().isInterrupted()) {
            if((hash = transactionsToSolidify.poll()) != null) {
                try {
                    checkSolidity(hash);
                } catch (Exception e) {
                    log.info(e.getMessage());
                }
            }
        }
    }

    /**
     *{@inheritDoc}
     */
    @Override
    public boolean checkSolidity(Hash hash) throws Exception {
        return checkSolidity(hash, Integer.MAX_VALUE);
    }

    /**
     *{@inheritDoc}
     */
   @Override
    public boolean checkSolidity(Hash hash, int maxProcessedTransactions) throws Exception {
        if(fromHash(tangle, hash).isSolid()) {
            return true;
        }
        LinkedHashSet<Hash> analyzedHashes = new LinkedHashSet<>(snapshotProvider.getInitialSnapshot().getSolidEntryPoints().keySet());
        if(maxProcessedTransactions != Integer.MAX_VALUE) {
            maxProcessedTransactions += analyzedHashes.size();
        }
        boolean solid = true;
        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(hash));
        Hash hashPointer;
        while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
            if (!analyzedHashes.add(hashPointer)) {
                continue;
            }

            if (analyzedHashes.size() >= maxProcessedTransactions) {
                return false;
            }

            TransactionViewModel transaction = fromHash(tangle, hashPointer);
            if (isUnsolidWithoutEntryPoint(transaction, hashPointer)) {
                if (transaction.getType() == PREFILLED_SLOT) {
                    solid = false;
                    checkRequester(hashPointer);
                } else {
                    nonAnalyzedTransactions.offer(transaction.getTrunkTransactionHash());
                    nonAnalyzedTransactions.offer(transaction.getBranchTransactionHash());
                }
            }
        }
        if (solid) {
          updateTransactions(analyzedHashes);
        }
        analyzedHashes.clear();
        return  solid;
    }


    /**
     * Check if a transaction is present in the {@link #transactionRequester}, if not, it is added.
     * @param hashPointer   The hash of the transaction to request
     */
    private void checkRequester(Hash hashPointer){
        if (!transactionRequester.isTransactionRequested(hashPointer)) {
            transactionRequester.requestTransaction(hashPointer);
        }
    }

    /**
     * Iterate through analyzed hashes and place them in the {@link #transactionsToBroadcast} queue
     * @param hashes    Analyzed hashes from the {@link #checkSolidity(Hash)} call
     */
    private void updateTransactions(Set<Hash> hashes) {
        hashes.forEach(hash -> {
            try {
                TransactionViewModel tvm = fromHash(tangle, hash);
                tvm.updateHeights(tangle, snapshotProvider.getInitialSnapshot());

                if(!tvm.isSolid()){
                    tvm.updateSolid(true);
                    tvm.update(tangle, snapshotProvider.getInitialSnapshot(), "solid|height");
                }
                addToBroadcastQueue(tvm);
            } catch (Exception e) {
                log.info(e.getMessage());
            }
        });
    }

    /**
     * Returns true if transaction is not solid and there are no solid entry points from the initial snapshot.
     */
    private boolean isUnsolidWithoutEntryPoint(TransactionViewModel transaction, Hash hashPointer){
        return (!transaction.isSolid() && !snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(hashPointer));
    }


    private void addToBroadcastQueue(TransactionViewModel tvm) {
        try {
            if (transactionsToBroadcast.size() >= MAX_SIZE) {
                transactionsToBroadcast.remove();
            }

            transactionsToBroadcast.put(tvm);
        } catch(Exception e){
            log.info("Error placing transaction into broadcast queue: " + e.getMessage());
        }
    }

    @VisibleForTesting
    Set<Hash> getSolidificationQueue(){
        return new LinkedHashSet<>(transactionsToSolidify);
    }
}
