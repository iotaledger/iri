package com.iota.iri.service.validation.impl;

import com.google.common.annotations.VisibleForTesting;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.pipeline.TransactionProcessingPipeline;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.service.validation.TransactionSolidifier;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.log.interval.IntervalLogger;
import com.iota.iri.utils.thread.DedicatedScheduledExecutorService;
import com.iota.iri.utils.thread.SilentScheduledExecutorService;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

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
     * Interval that the {@link #transactionSolidifierThread()} will scan at.
     */
    private static final int RESCAN_INTERVAL = 500;

    private static final IntervalLogger log = new IntervalLogger(TransactionSolidifier.class);

    /**
     * Executor service for running the {@link #transactionSolidifierThread()}.
     */
    private SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Transaction Solidifier", log.delegate());

    /**
     * A queue for processing transactions with the {@link #checkSolidity(Hash)} call. Once a transaction has been
     * marked solid it will be placed into the {@link #transactionsToUpdate} queue.
     */
    private Queue<Hash> transactionsToSolidify = new ArrayBlockingQueue<>(1000);
    /**
     * A queue for processing transactions with the {@link #updateSolidTransactions(Tangle, Snapshot, Hash)} call.
     * Once the transaction is updated it is placed into the {@link #transactionsToBroadcast} set.
     */
    private Queue<Hash> transactionsToUpdate = new ArrayBlockingQueue<>(1000);
    /**
     * A set of transactions that will be called by the {@link TransactionProcessingPipeline} to be broadcast to
     * neighboring nodes.
     */
    private Queue<TransactionViewModel> transactionsToBroadcast = new ArrayBlockingQueue<>(1000);
    /**
     * A set containing the hash of already solidified transactions
     */
    private Set<Hash> solidified = new LinkedHashSet<>();

    /**
     * Max size and buffer for {@link #solidified} set.
     */
    private static final int MAX_SIZE= 5000;
    private static final int BUFFER = 2500;


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
        executorService.silentScheduleWithFixedDelay(this::transactionSolidifierThread, 0, RESCAN_INTERVAL,
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
            if(!solidified.contains(hash) && !transactionsToSolidify.contains(hash)) {
                    transactionsToSolidify.add(hash);
            }

            // Clear room in the solidified set for newer transaction hashes
            if(transactionsToSolidify.size() > MAX_SIZE){
                popElderTransactions();
            }
        } catch(Exception e){
            log.error(e.getMessage());
        }
    }

    /**
     * Clears the older half of the {@link #solidified} set to reduce memory footprint.
     */
    private void popElderTransactions(){
        try{
            Iterator<Hash> solidifiedIterator = transactionsToSolidify.iterator();
            for(int i = 0; i < BUFFER; i++){
                solidifiedIterator.next();
                solidifiedIterator.remove();
            }
        } catch(Exception e){
            log.info(e.getMessage());
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
     * Main thread. Process the {@link #transactionsToSolidify} and {@link #transactionsToUpdate} queue's.
     */
    private void transactionSolidifierThread(){
        processTransactionsToSolidify();
        processTransactionsToUpdate();

    }

    /**
     * Iterate through the {@link #transactionsToSolidify} queue and call {@link #checkSolidity(Hash)} on each hash.
     * Solid transactions are added to the {@link #updateSolidTransactions(Tangle, Snapshot, Hash)} set.
     */
    private void processTransactionsToSolidify(){
        Iterator<Hash> solidificationIterator = transactionsToSolidify.iterator();
        while (!Thread.currentThread().isInterrupted() && solidificationIterator.hasNext()) {
            try {
                checkSolidity(solidificationIterator.next());
                solidificationIterator.remove();
            } catch (Exception e) {
                log.info(e.getMessage());
            }
        }
    }

    /**
     * Iterate through the {@link #transactionsToUpdate} queue and call
     * {@link #updateSolidTransactions(Tangle, Snapshot, Hash)} on each hash. Passes updated transactions to the
     * {@link #transactionsToBroadcast} set.
     */
    private void processTransactionsToUpdate(){
            Iterator<Hash> updateIterator = transactionsToUpdate.iterator();
            while(!Thread.currentThread().isInterrupted() && updateIterator.hasNext()){
                try{
                    updateSolidTransactions(tangle, snapshotProvider.getInitialSnapshot(), updateIterator.next());
                    updateIterator.remove();

                } catch(Exception e){
                    log.info(e.getMessage());
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
          addToUpdateQueue(analyzedHashes);
        }
        analyzedHashes.clear();
        return  solid;
    }

    /**
     * Updates the solidity of a transaction object and places it into the {@link #transactionsToBroadcast} set.
     * @param tangle            The DB reference
     * @param initialSnapshot   Initial snapshot from the {@link #snapshotProvider}
     * @param analyzedHash      Hash of the transaction that is being updated
     * @throws Exception        Throws an exception if there is an error updating the transaction object's solidity
     */
    private void updateSolidTransactions(Tangle tangle, Snapshot initialSnapshot, Hash analyzedHash)
            throws Exception {
        TransactionViewModel transactionViewModel = fromHash(tangle, analyzedHash);

        transactionViewModel.updateHeights(tangle, initialSnapshot);

        if (!transactionViewModel.isSolid()) {
            transactionViewModel.updateSolid(true);
            transactionViewModel.update(tangle, initialSnapshot, "solid|height");
        }
        addToBroadcastQueue(transactionViewModel);
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
     * Iterate through analyzed hashes and place them in the {@link #transactionsToUpdate} queue
     * @param hashes    Analyzed hashes from the {@link #checkSolidity(Hash)} call
     */
    private void addToUpdateQueue(Set<Hash> hashes) {
        hashes.forEach(hash -> {
            try {
                solidified.add(hash);
                if(!transactionsToUpdate.contains(hash)) {
                    transactionsToUpdate.add(hash);
                }
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void addToBroadcastQueue(TransactionViewModel tvm){
        transactionsToBroadcast.add(tvm);
    }

    @VisibleForTesting
    Set<Hash> getSolidificationQueue(){
        return new LinkedHashSet<>(transactionsToSolidify);
    }
}
