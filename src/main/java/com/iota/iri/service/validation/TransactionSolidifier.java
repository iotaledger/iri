package com.iota.iri.service.validation;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.persistables.Milestone;
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

public class TransactionSolidifier {

    private Tangle tangle;
    private SnapshotProvider snapshotProvider;
    private TransactionRequester transactionRequester;

    private static int RESCAN_INTERVAL = 2000;

    private static final IntervalLogger log = new IntervalLogger(TransactionSolidifier.class);


    private SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Transaction Solidifier", log.delegate());

    private BlockingQueue<Hash> transactionsToSolidify = new ArrayBlockingQueue(500);
    private BlockingQueue<Hash> transactionsToUpdate = new ArrayBlockingQueue(500);

    private Set<TransactionViewModel> transactionsToBroadcast = new LinkedHashSet<>();
    private Set<Hash> solidified = new LinkedHashSet<>();
    private Object broadcastSync = new Object();


    public TransactionSolidifier(Tangle tangle, SnapshotProvider snapshotProvider, TransactionRequester transactionRequester){
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.transactionRequester = transactionRequester;
    }


    public void start(){
        executorService.silentScheduleWithFixedDelay(this::transactionSolidifierThread, 0, RESCAN_INTERVAL,
                TimeUnit.MILLISECONDS);
    }


    public void shutdown() {
        executorService.shutdownNow();
    }

    public boolean addToSolidificationQueue(Hash hash, boolean milestone){
        try{
            if(!solidified.contains(hash) && !transactionsToSolidify.contains(hash)) {
                transactionsToSolidify.put(hash);
                if(milestone){
                    return checkSolidity(hash, 50000);
                }
            }

            return checkSolidity(hash);

        } catch(Exception e){
            e.getStackTrace();
            return false;
        }
    }


    public Set<TransactionViewModel> getBroadcastQueue(){
        synchronized (broadcastSync) {
            Set<TransactionViewModel> broadcastQueue = new LinkedHashSet<TransactionViewModel>();
            broadcastQueue.addAll(transactionsToBroadcast);
            return broadcastQueue;
        }
    }


    public void clearBroadcastQueue(Set<TransactionViewModel> transactionsBroadcasted){
        synchronized (broadcastSync) {
            for (TransactionViewModel tvm : transactionsBroadcasted) {
                transactionsToBroadcast.remove(tvm);
            }
        }
    }


    private void transactionSolidifierThread(){
        processTransactionsToSolidify();
        processTransactionsToUpdate();
    }

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


    public void updateSolidTransactions(Tangle tangle, Snapshot initialSnapshot, Hash analyzedHash)
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
