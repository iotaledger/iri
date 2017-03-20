package com.iota.iri.service;

import com.iota.iri.Milestone;
import com.iota.iri.model.*;
import com.iota.iri.service.storage.AbstractStorage;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.service.viewModels.TransactionViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.iota.iri.service.storage.AbstractStorage.*;

/**
 * Created by paul on 3/8/17 for iri.
 */
public class ScratchpadViewModel {
    private static final Logger log = LoggerFactory.getLogger(ScratchpadViewModel.class);

    protected static final byte[] ZEROED_BUFFER = new byte[CELL_SIZE];
    Set<BigInteger> requestSet = new TreeSet<>();

    static long lastTime = 0L;

    public static ScratchpadViewModel instance = new ScratchpadViewModel();

    public boolean setAnalyzedTransactionFlag(byte[] hash) throws ExecutionException, InterruptedException {
        AnalyzedFlag flag = new AnalyzedFlag();
        flag.hash = hash;
        return !Tangle.instance().save(flag).get();
    }

    public void clearAnalyzedTransactionsFlags() throws Exception {
        Tangle.instance().flushAnalyzedFlags().get();
    }

    public int getNumberOfTransactionsToRequest() throws ExecutionException, InterruptedException {
        return Tangle.instance().getNumberOfRequestedTransactions().get().intValue();
    }

    public Future<Void> clearReceivedTransaction(byte[] hash) throws ExecutionException, InterruptedException {
        Scratchpad scratchpad = new Scratchpad();
        scratchpad.hash = hash;
        return Tangle.instance().delete(scratchpad);
    }

    public void rescanTransactionsToRequest() throws Exception {
        LinkedHashSet<String> nonAnalyzedTransactions = new LinkedHashSet<>(Collections.singleton(Milestone.latestMilestone.toString()));
        LinkedHashSet<String> analyzedTransactions = new LinkedHashSet<>(Collections.singleton(Milestone.latestMilestone.toString()));
        Hash hash;
        Tangle.instance().flushScratchpad().get();
        while(nonAnalyzedTransactions.size() != 0) {
            hash = new Hash((String) nonAnalyzedTransactions.toArray()[0]);
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(hash);
            nonAnalyzedTransactions.remove(hash.toString());
            if(transactionViewModel.getType() == AbstractStorage.PREFILLED_SLOT) {
                ScratchpadViewModel.instance().requestTransaction(transactionViewModel.getHash());
            } else {
                if(analyzedTransactions.add((new Hash(transactionViewModel.getTrunkTransactionHash()).toString())))
                    nonAnalyzedTransactions.add(new Hash(transactionViewModel.getTrunkTransactionHash()).toString());
                if(analyzedTransactions.add(new Hash(transactionViewModel.getBranchTransactionHash()).toString()))
                    nonAnalyzedTransactions.add(new Hash(transactionViewModel.getBranchTransactionHash()).toString());
            }
        }
        log.info("number of analyzed tx: " + analyzedTransactions.size());
    }

    public Future<Boolean> requestTransaction(byte[] hash) throws ExecutionException, InterruptedException {
        Scratchpad scratchpad = new Scratchpad();
        scratchpad.hash = hash;
        return Tangle.instance().save(scratchpad);
    }

    public void transactionToRequest(byte[] buffer, int offset) throws ExecutionException, InterruptedException {
        final long beginningTime = System.nanoTime();//System.currentTimeMillis();
        Scratchpad scratchpad = ((Scratchpad) Tangle.instance().getLatest(Scratchpad.class).get());

        if(scratchpad != null && !Arrays.equals(scratchpad.hash, TransactionViewModel.NULL_TRANSACTION_HASH_BYTES)) {
            requestSet.add(new BigInteger(scratchpad.hash));
            //log.info("Tx to Request: " + new Hash(scratchpad.hash));
            System.arraycopy(scratchpad.hash, 0, buffer, offset, TransactionViewModel.HASH_SIZE);
        }
        long now = System.nanoTime();
        if ((now - lastTime) > 10000000000L) {
            lastTime = now;
            log.info("Transactions to request = {}", getNumberOfTransactionsToRequest() + " / " + TransactionViewModel.getNumberOfStoredTransactions() + " (" + (now - beginningTime)/1000 + " us ). Request count: " + requestSet.size());
            requestSet.clear();
        }
    }

    public static ScratchpadViewModel instance() {
        return instance;
    }
}
