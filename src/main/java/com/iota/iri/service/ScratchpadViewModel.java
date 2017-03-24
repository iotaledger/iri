package com.iota.iri.service;

import com.iota.iri.Milestone;
import com.iota.iri.model.*;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.service.viewModels.TransactionViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Created by paul on 3/8/17 for iri.
 */
public class ScratchpadViewModel {
    private static final Logger log = LoggerFactory.getLogger(ScratchpadViewModel.class);

    static long lastTime = 0L;

    public static ScratchpadViewModel instance = new ScratchpadViewModel();

    public boolean setAnalyzedTransactionFlag(Hash hash) throws ExecutionException, InterruptedException {
        AnalyzedFlag flag = new AnalyzedFlag();
        flag.hash = hash;
        return !Tangle.instance().save(flag).get();
    }

    public void clearAnalyzedTransactionsFlags() throws Exception {
        Tangle.instance().flush(AnalyzedFlag.class).get();
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
        Set<Hash> nonAnalyzedTransactions;
        Set<Hash> analyzedTransactions;
        nonAnalyzedTransactions = new HashSet<>(Collections.singleton(Milestone.latestMilestone));
        analyzedTransactions = new HashSet<>(Collections.singleton(Milestone.latestMilestone));
        Hash hash;
        Tangle.instance().flush(Scratchpad.class).get();
        while(nonAnalyzedTransactions.size() != 0) {
            hash = (Hash) nonAnalyzedTransactions.toArray()[0];
            nonAnalyzedTransactions.remove(hash);
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(hash);
            if(transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT && !transactionViewModel.getHash().equals(Hash.NULL_HASH)) {
                //log.info("Trasaction Hash to Request: " + new Hash(transactionViewModel.getHash()));
                ScratchpadViewModel.instance().requestTransaction(transactionViewModel.getHash());
            } else {
                if(analyzedTransactions.add(transactionViewModel.getTrunkTransactionHash()))
                    nonAnalyzedTransactions.add(transactionViewModel.getTrunkTransactionHash());
                if(analyzedTransactions.add(transactionViewModel.getBranchTransactionHash()))
                    nonAnalyzedTransactions.add(transactionViewModel.getBranchTransactionHash());
            }
        }
        log.info("number of analyzed tx: " + analyzedTransactions.size());
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
