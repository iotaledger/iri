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

    static long lastTime = 0L;

    public static ScratchpadViewModel instance = new ScratchpadViewModel();

    public boolean setAnalyzedTransactionFlag(BigInteger hash) throws ExecutionException, InterruptedException {
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

    public Future<Void> clearReceivedTransaction(BigInteger hash) throws ExecutionException, InterruptedException {
        Scratchpad scratchpad = new Scratchpad();
        scratchpad.hash = hash;
        return Tangle.instance().delete(scratchpad);
    }

    public void rescanTransactionsToRequest() throws Exception {
        Set<BigInteger> nonAnalyzedTransactions;
        Set<BigInteger> analyzedTransactions;
        nonAnalyzedTransactions = new HashSet<>(Collections.singleton(Milestone.latestMilestone));
        analyzedTransactions = new HashSet<>(Collections.singleton(Milestone.latestMilestone));
        Hash hash;
        Tangle.instance().flushScratchpad().get();
        while(nonAnalyzedTransactions.size() != 0) {
            BigInteger nextHashInteger = (BigInteger) nonAnalyzedTransactions.toArray()[0];
            hash = new Hash(nextHashInteger);
            nonAnalyzedTransactions.remove(nextHashInteger);
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(hash);
            if(transactionViewModel.getType() == AbstractStorage.PREFILLED_SLOT && !transactionViewModel.getHash().equals(TransactionViewModel.PADDED_NULL_HASH)) {
                //log.info("Trasaction Hash to Request: " + new Hash(transactionViewModel.getHash()));
                ScratchpadViewModel.instance().requestTransaction(transactionViewModel.getHash());
            } else {
                if(analyzedTransactions.add(transactionViewModel.getTrunkTransactionPointer()))
                    nonAnalyzedTransactions.add(transactionViewModel.getTrunkTransactionPointer());
                if(analyzedTransactions.add(transactionViewModel.getBranchTransactionPointer()))
                    nonAnalyzedTransactions.add(transactionViewModel.getBranchTransactionPointer());
            }
        }
        log.info("number of analyzed tx: " + analyzedTransactions.size());
    }

    public Future<Boolean> requestTransaction(BigInteger hash) throws ExecutionException, InterruptedException {
        Scratchpad scratchpad = new Scratchpad();
        scratchpad.hash = hash;
        return Tangle.instance().save(scratchpad);
    }

    public void transactionToRequest(byte[] buffer, int offset) throws ExecutionException, InterruptedException {
        final long beginningTime = System.currentTimeMillis();
        Scratchpad scratchpad = ((Scratchpad) Tangle.instance().getLatest(Scratchpad.class).get());

        if(scratchpad != null && scratchpad.hash != null && !scratchpad.hash.equals(TransactionViewModel.PADDED_NULL_HASH)) {
            System.arraycopy(Hash.padHash(scratchpad.hash), 0, buffer, offset, TransactionViewModel.HASH_SIZE);
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
