package com.iota.iri.service;

import com.iota.iri.Milestone;
import com.iota.iri.model.*;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.service.viewModels.TipsViewModel;
import com.iota.iri.service.viewModels.TransactionViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Created by paul on 3/8/17 for iri.
 */
public class ScratchpadViewModel {
    private static final Logger log = LoggerFactory.getLogger(ScratchpadViewModel.class);

    static long lastTime = 0L;

    public static ScratchpadViewModel instance = new ScratchpadViewModel();

    public boolean setAnalyzedTransactionFlag(int id, Hash hash) throws ExecutionException, InterruptedException {
        return Tangle.instance().save(id, new Flag(hash)).get();
    }

    public void clearAnalyzedTransactionsFlags(int id) throws Exception {
        Tangle.instance().flushTransientFlags(id).get();
    }
    public void releaseAnalyzedTransactionsFlags(int id) throws Exception {
        Tangle.instance().releaseTransientTable(id);
    }

    public int getAnalyzedTransactionTable() throws Exception {
        return Tangle.instance().createTransientFlagList();
    }

    public int getNumberOfTransactionsToRequest() throws ExecutionException, InterruptedException {
        return Tangle.instance().getCount(Scratchpad.class).get().intValue();
    }

    public Future<Void> clearReceivedTransaction(Hash hash) throws ExecutionException, InterruptedException {
        Scratchpad scratchpad = new Scratchpad();
        scratchpad.hash = hash;
        return Tangle.instance().delete(scratchpad);
    }

    public void rescanTransactionsToRequest() throws Exception {
        int id = getAnalyzedTransactionTable();
        Queue<Hash> nonAnalyzedTransactions;
        nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(Milestone.latestMilestone));
        nonAnalyzedTransactions.addAll(Arrays.asList(TipsViewModel.getTipHashes()));
        Hash hash;
        Tangle.instance().flush(Scratchpad.class).get();
        while((hash = nonAnalyzedTransactions.poll()) != null) {
            if(setAnalyzedTransactionFlag(id, hash)) {
                TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(hash);
                if(transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT && !transactionViewModel.getHash().equals(Hash.NULL_HASH)) {
                    //log.info("Trasaction Hash to Request: " + new Hash(transactionViewModel.getHash()));
                    ScratchpadViewModel.instance().requestTransaction(transactionViewModel.getHash());
                } else {
                    nonAnalyzedTransactions.add(transactionViewModel.getTrunkTransactionHash());
                    nonAnalyzedTransactions.add(transactionViewModel.getBranchTransactionHash());
                }
            }
        }
        log.info("number of analyzed tx: " + Tangle.instance().getCount(id).get());
        releaseAnalyzedTransactionsFlags(id);
    }

    public Future<Boolean> requestTransaction(Hash hash) throws ExecutionException, InterruptedException {
        Scratchpad scratchpad = new Scratchpad();
        scratchpad.hash = hash;
        return Tangle.instance().save(scratchpad);
    }

    public void transactionToRequest(byte[] buffer, int offset) throws ExecutionException, InterruptedException {
        final long beginningTime = System.currentTimeMillis();
        Scratchpad scratchpad = ((Scratchpad) Tangle.instance().getLatest(Scratchpad.class).get());

        if(scratchpad != null && scratchpad.hash != null && !scratchpad.hash.equals(Hash.NULL_HASH)) {
            System.arraycopy(scratchpad.hash.bytes(), 0, buffer, offset, TransactionViewModel.HASH_SIZE);
        }
        long now = System.currentTimeMillis();
        if ((now - lastTime) > 10000L) {
            lastTime = now;
            log.info("Transactions to request = {}", getNumberOfTransactionsToRequest() + " / " + TransactionViewModel.getNumberOfStoredTransactions() + " (" + (now - beginningTime) + " ms ). " );
        }
    }

    public static ScratchpadViewModel instance() {
        return instance;
    }
}
