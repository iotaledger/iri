package com.iota.iri.service.validation.impl;

import com.google.common.annotations.VisibleForTesting;
import com.iota.iri.controllers.TipsViewModel;
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

    private static final int SOLIDIFICATION_INTERVAL = 100;

    private static final IntervalLogger log = new IntervalLogger(TransactionSolidifier.class);

    /**
     * Executor service for running the {@link #processTransactionsToSolidify()}.
     */
    private SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Transaction Solidifier", log.delegate());

    /**
     * A set of transactions that will be called by the {@link TransactionProcessingPipeline} to be broadcast to
     * neighboring nodes.
     */
    private BlockingQueue<TransactionViewModel> transactionsToBroadcast = new ArrayBlockingQueue<>(MAX_SIZE);

    private TipsViewModel tipsViewModel;

    private TransactionPropagator transactionPropagator;

    /**
     * Constructor for the solidifier.
     * @param tangle                    The DB reference
     * @param snapshotProvider          For fetching entry points for solidity checks
     * @param transactionRequester      A requester for missing transactions
     */
    public TransactionSolidifierImpl(Tangle tangle, SnapshotProvider snapshotProvider, TransactionRequester transactionRequester,
                                     TipsViewModel tipsViewModel){
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.transactionRequester = transactionRequester;
        this.tipsViewModel = tipsViewModel;
        this.transactionPropagator = new TransactionPropagator();
    }

    /**
     *{@inheritDoc}
     */
    @Override
    public void start(){
        executorService.silentScheduleWithFixedDelay(this::processTransactionsToSolidify, 0,
                SOLIDIFICATION_INTERVAL, TimeUnit.MILLISECONDS);
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
    public TransactionViewModel getNextTxInBroadcastQueue(){
        return transactionsToBroadcast.poll();
    }

    /**
     *{@inheritDoc}
     */
    @Override
    public void clearFromBroadcastQueue(TransactionViewModel transaction){
        transactionsToBroadcast.remove(transaction);
    }


    /**
     * Process any solid transactions present in the {@link TransactionPropagator}.
     */
    private void processTransactionsToSolidify(){
        transactionPropagator.propagateSolidTransactions();
    }

    /**
     *{@inheritDoc}
     */
    @Override
    public boolean checkSolidity(Hash hash) throws Exception {
        return checkSolidity(hash, 50000);
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
        maxProcessedTransactions = Math.addExact(maxProcessedTransactions, analyzedHashes.size());
        boolean solid = true;
        final Deque<Hash> nonAnalyzedTransactions = new ArrayDeque<>(Collections.singleton(hash));
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
                transactionPropagator.addToPropagationQueue(tvm.getHash());
            } catch (Exception e) {
                log.info(e.getMessage());
            }
        });
    }

    /**
     * Returns true if transaction is not solid and there are no solid entry points from the initial snapshot.
     */
    private boolean isUnsolidWithoutEntryPoint(TransactionViewModel transaction, Hash hashPointer) throws Exception{
        if(!transaction.isSolid() && !snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(hashPointer)){
            return true;
        }
        transactionPropagator.addToPropagationQueue(hashPointer);
        return false;
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

    @Override
    public void updateStatus(TransactionViewModel transactionViewModel) throws Exception {
        transactionRequester.clearTransactionRequest(transactionViewModel.getHash());
        if(transactionViewModel.getApprovers(tangle).size() == 0) {
            tipsViewModel.addTipHash(transactionViewModel.getHash());
        }
        tipsViewModel.removeTipHash(transactionViewModel.getTrunkTransactionHash());
        tipsViewModel.removeTipHash(transactionViewModel.getBranchTransactionHash());

        if(quickSetSolid(transactionViewModel)) {
            transactionViewModel.update(tangle, snapshotProvider.getInitialSnapshot(), "solid|height");
            tipsViewModel.setSolid(transactionViewModel.getHash());
            transactionPropagator.addToPropagationQueue(transactionViewModel.getHash());
        }
    }


    @Override
    public boolean quickSetSolid(final TransactionViewModel transactionViewModel) throws Exception {
        if(!transactionViewModel.isSolid()) {
            boolean solid = true;
            if (!checkApproovee(transactionViewModel.getTrunkTransaction(tangle))) {
                solid = false;
            }
            if (!checkApproovee(transactionViewModel.getBranchTransaction(tangle))) {
                solid = false;
            }
            if(solid) {
                transactionViewModel.updateSolid(true);
                transactionViewModel.updateHeights(tangle, snapshotProvider.getInitialSnapshot());
                transactionPropagator.addToPropagationQueue(transactionViewModel.getHash());
                addToBroadcastQueue(transactionViewModel);
                return true;
            }
        }
        return false;
    }

    /**
     * If the the {@code approvee} is missing, request it from a neighbor.
     * @param approovee transaction we check.
     * @return true if {@code approvee} is solid.
     * @throws Exception if we encounter an error while requesting a transaction
     */
    private boolean checkApproovee(TransactionViewModel approovee) throws Exception {
        if(snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(approovee.getHash())) {
            return true;
        }
        if(approovee.getType() == PREFILLED_SLOT) {
            // don't solidify from the bottom until cuckoo filters can identify where we deleted -> otherwise we will
            // continue requesting old transactions forever
            //transactionRequester.requestTransaction(approovee.getHash(), false);
            return false;
        }
        return approovee.isSolid();
    }

    /**
     *  A transaction solidification service that propagates upwards through transactions approving a solid transaction
     *  and performs {@link #quickSetSolid} checks to determine their solidity as well
     */
    private class TransactionPropagator {
        /**
         * A queue for processing transactions with the {@link #propagateSolidTransactions()} call. This will check
         * approving transactions with {@link #quickSetSolid(TransactionViewModel)}.
         */
        private BlockingQueue<Hash> solidTransactions = new ArrayBlockingQueue<>(MAX_SIZE);

        /**
         * Add to the propagation queue where it will be processed to help solidify approving transactions faster
         * @param hash      The transaction hash to be removed
         * @throws Exception
         */
        public void addToPropagationQueue(Hash hash) throws Exception{
            if(!solidTransactions.contains(hash)) {
                if (solidTransactions.size() >= MAX_SIZE) {
                    solidTransactions.poll();
                }
                solidTransactions.put(hash);
            }
        }

        @VisibleForTesting
        void propagateSolidTransactions() {
            while(!Thread.currentThread().isInterrupted() && solidTransactions.peek() != null) {
                try {
                    Hash hash = solidTransactions.poll();
                    TransactionViewModel transaction = fromHash(tangle, hash);
                    Set<Hash> approvers = transaction.getApprovers(tangle).getHashes();
                    for(Hash h: approvers) {
                        TransactionViewModel tx = fromHash(tangle, h);
                        if (quietQuickSetSolid(tx)) {
                            tx.update(tangle, snapshotProvider.getInitialSnapshot(), "solid|height");
                            tipsViewModel.setSolid(h);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error while propagating solidity upwards", e);
                }
            }
        }

        /**
         * Perform a {@link #quickSetSolid} while capturing and logging errors
         * @param transactionViewModel transaction we try to solidify.
         * @return <tt>true</tt> if we managed to solidify, else <tt>false</tt>.
         */
        private boolean quietQuickSetSolid(TransactionViewModel transactionViewModel) {
            try {
                return quickSetSolid(transactionViewModel);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                return false;
            }
        }
    }
}