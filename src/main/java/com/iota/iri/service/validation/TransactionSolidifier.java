package com.iota.iri.service.validation;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.network.pipeline.TransactionProcessingPipeline;
import com.iota.iri.network.TransactionRequester;
import com.iota.iri.service.snapshot.Snapshot;
import com.iota.iri.service.snapshot.SnapshotProvider;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.log.interval.IntervalLogger;
import com.iota.iri.utils.thread.DedicatedScheduledExecutorService;
import com.iota.iri.utils.thread.SilentScheduledExecutorService;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.iota.iri.controllers.TransactionViewModel.PREFILLED_SLOT;
import static com.iota.iri.controllers.TransactionViewModel.fromHash;

/**
 * A solidifier class for processing transactions. Transactions are checked for solidity, and missing transactions are
 * subsequently requested. Once a transaction is solidified correctly it is placed into a broadcasting set to be sent to
 * neighboring nodes.
 */
public class TransactionSolidifier {

    private Tangle tangle;
    private SnapshotProvider snapshotProvider;
    private TransactionRequester transactionRequester;

    /**
     * Interval that the {@link #transactionSolidifierThread()} will scan at.
     */
    private static int RESCAN_INTERVAL = 5000;

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
    private BlockingQueue<Hash> transactionsToSolidify = new ArrayBlockingQueue(500);
    /**
     * A queue for processing transactions with the {@link #updateSolidTransactions(Tangle, Snapshot, Hash)} call.
     * Once the transaction is updated it is placed into the {@link #transactionsToBroadcast} set.
     */
    private BlockingQueue<Hash> transactionsToUpdate = new ArrayBlockingQueue(500);
    /**
     * A set of transactions that will be called by the {@link TransactionProcessingPipeline} to be broadcast to
     * neighboring nodes.
     */
    private Set<TransactionViewModel> transactionsToBroadcast = new LinkedHashSet<>();
    /**
     * A set containing the hash of already solidified transactions
     */
    private Set<Hash> solidified = new LinkedHashSet<>();
    /**
     * An object for synchronising access to the {@link #transactionsToBroadcast} set.
     */
    private final Object broadcastSync = new Object();

    /**
     * Max size and buffer for {@link #solidified} set.
     */
    private static int MAX_SIZE= 10000;
    private static int BUFFER = 5000;


    /**
     * Constructor for the solidifier.
     * @param tangle                    The DB reference
     * @param snapshotProvider          For fetching entry points for solidity checks
     * @param transactionRequester      A requester for missing transactions
     */
    public TransactionSolidifier(Tangle tangle, SnapshotProvider snapshotProvider, TransactionRequester transactionRequester){
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.transactionRequester = transactionRequester;
    }

    /**
     * Initialize the executor service.
     */
    public void start(){
        executorService.silentScheduleWithFixedDelay(this::transactionSolidifierThread, 0, RESCAN_INTERVAL,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Interrupt thread processes and shut down the executor service.
     */
    public void shutdown() {
        executorService.shutdownNow();
    }

    /**
     * Add a hash to the solidification queue, and runs an initial {@link #checkSolidity} call.
     *
     * @param hash      Hash of the transaction to solidify
     * @return          True if the transaction is solid, False if not
     */
    public void addToSolidificationQueue(Hash hash){
        try{
            if(!solidified.contains(hash) && !transactionsToSolidify.contains(hash)) {
                transactionsToSolidify.put(hash);
            }

            checkSolidity(hash);
            // Clear room in the solidified set for newer transaction hashes
            if(solidified.size() > MAX_SIZE){
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
            Iterator<Hash> solidifiedIterator = solidified.iterator();
            for(int i = 0; i < BUFFER; i++){
                solidifiedIterator.next();
                solidifiedIterator.remove();
            }
        } catch(Exception e){
            log.info(e.getMessage());
        }
    }


    /**
     * Fetch a copy of the current {@link #transactionsToBroadcast} set.
     * @return          A set of {@link TransactionViewModel} objects to be broadcast.
     */
    public Set<TransactionViewModel> getBroadcastQueue(){
        synchronized (broadcastSync) {
            Set<TransactionViewModel> broadcastQueue = new LinkedHashSet<TransactionViewModel>();
            broadcastQueue.addAll(transactionsToBroadcast);
            return broadcastQueue;
        }
    }

    /**
     * Remove any broadcasted transactions from the {@link #transactionsToBroadcast} set
     * @param transactionsBroadcasted   A set of {@link TransactionViewModel} objects to remove from the set.
     */
    public void clearBroadcastQueue(Set<TransactionViewModel> transactionsBroadcasted){
        synchronized (broadcastSync) {
            for (TransactionViewModel tvm : transactionsBroadcasted) {
                transactionsToBroadcast.remove(tvm);
            }
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
        while(!Thread.currentThread().isInterrupted() && solidificationIterator.hasNext()){
            try {
                Hash hash = solidificationIterator.next();
                checkSolidity(hash);
                solidificationIterator.remove();
            } catch(Exception e ){
                e.printStackTrace();
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
                Hash hash = updateIterator.next();
                updateSolidTransactions(tangle, snapshotProvider.getInitialSnapshot(), hash);
                updateIterator.remove();
            } catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
     * This method does the same as {@link #checkSolidity(Hash, int)} but defaults to an unlimited amount
     * of transactions that are allowed to be traversed.
     *
     * @param hash hash of the transactions that shall get checked
     * @return true if the transaction is solid and false otherwise
     * @throws Exception if anything goes wrong while trying to solidify the transaction
     */

    public boolean checkSolidity(Hash hash) throws Exception {
        return checkSolidity(hash, Integer.MAX_VALUE);
    }

    /**
     * This method checks transactions for solidity and marks them accordingly if they are found to be solid.
     *
     * It iterates through all approved transactions until it finds one that is missing in the database or until it
     * reached solid transactions on all traversed subtangles. In case of a missing transactions it issues a transaction
     * request and returns false. If no missing transaction is found, it marks the processed transactions as solid in
     * the database and returns true.
     *
     * Since this operation can potentially take a long time to terminate if it would have to traverse big parts of the
     * tangle, it is possible to limit the amount of transactions that are allowed to be processed, while looking for
     * unsolid / missing approvees. This can be useful when trying to "interrupt" the solidification of one transaction
     * (if it takes too many steps) to give another one the chance to be solidified instead (i.e. prevent blocks in the
     * solidification threads).
     *
     * @param hash hash of the transactions that shall get checked
     * @param maxProcessedTransactions the maximum amount of transactions that are allowed to be traversed
     * @return true if the transaction is solid and false otherwise
     * @throws Exception if anything goes wrong while trying to solidify the transaction
     */
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
            if (!transaction.isSolid() && !snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(hashPointer)) {
                if (transaction.getType() == PREFILLED_SLOT) {
                    solid = false;

                    if (!transactionRequester.isTransactionRequested(hashPointer)) {
                        transactionRequester.requestTransaction(hashPointer);
                        continue;
                    }
                } else {
                    nonAnalyzedTransactions.offer(transaction.getTrunkTransactionHash());
                    nonAnalyzedTransactions.offer(transaction.getBranchTransactionHash());
                }
            }
        }
        if (solid) {
            analyzedHashes.forEach(h -> {
                try {
                    solidified.add(h);
                    transactionsToUpdate.put(h);
                } catch(Exception e){
                    e.printStackTrace();
                }
            });
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
        TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, analyzedHash);

        transactionViewModel.updateHeights(tangle, initialSnapshot);

        if (!transactionViewModel.isSolid()) {
            transactionViewModel.updateSolid(true);
            transactionViewModel.update(tangle, initialSnapshot, "solid|height");
        }
        synchronized (broadcastSync) {
            transactionsToBroadcast.add(transactionViewModel);
        }
    }
}
