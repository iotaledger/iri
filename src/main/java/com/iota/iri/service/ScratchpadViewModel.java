package com.iota.iri.service;

import com.iota.iri.model.*;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.service.viewModels.TransactionViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public volatile int numberOfTransactionsToRequest;

    static long lastTime = 0L;

    public static ScratchpadViewModel instance = new ScratchpadViewModel();
    private Object analyzedTransactionHandle;

    public ScratchpadViewModel() {
        try {
            analyzedTransactionHandle = Tangle.instance().createTransientList(AnalyzedFlag.class);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    public boolean setAnalyzedTransactionFlag(byte[] hash) throws ExecutionException, InterruptedException {
        AnalyzedFlag flag = new AnalyzedFlag();
        flag.hash = hash;
        if(!Tangle.instance().transientExists(analyzedTransactionHandle, flag.hash).get()) {
            Tangle.instance().save(analyzedTransactionHandle, flag).get();
            return true;
        }
        return false;
    }

    public void clearAnalyzedTransactionsFlags() throws Exception {
        Tangle.instance().dropList(analyzedTransactionHandle);
        analyzedTransactionHandle = Tangle.instance().createTransientList(AnalyzedFlag.class);
    }

    public int getNumberOfTransactionsToRequest() {
        return numberOfTransactionsToRequest;
    }

    public Future<Void> clearReceivedTransaction(byte[] hash) throws ExecutionException, InterruptedException {
        Scratchpad scratchpad = new Scratchpad();
        scratchpad.hash = hash;
        if(Tangle.instance().maybeHas(scratchpad).get()) {
            if(numberOfTransactionsToRequest != 0)
                numberOfTransactionsToRequest--;
            return Tangle.instance().delete(scratchpad);
        }
        return null;
    }

    public Future<Boolean> requestTransaction(byte[] hash) throws ExecutionException, InterruptedException {
        //if(!TransactionViewModel.mightExist(hash) || !TransactionViewModel.exists(hash)) {
        if(!TransactionViewModel.mightExist(hash)) {
            Scratchpad scratchpad = new Scratchpad();
            scratchpad.hash = hash;
            numberOfTransactionsToRequest++;
            return Tangle.instance().save(scratchpad);
        }
        return null;
    }

    public void transactionToRequest(byte[] buffer, int offset) throws ExecutionException, InterruptedException {
        final long beginningTime = System.nanoTime();//System.currentTimeMillis();
        Scratchpad scratchpad = ((Scratchpad) Tangle.instance().getLatest(Scratchpad.class).get());

        if(scratchpad != null && !Arrays.equals(scratchpad.hash, TransactionViewModel.NULL_TRANSACTION_HASH_BYTES)) {
            System.arraycopy(scratchpad.hash, 0, buffer, offset, TransactionViewModel.HASH_SIZE);
        }
        long now = System.nanoTime();
        if ((now - lastTime) > 10000000000L) {
            lastTime = now;
            //log.info("Transactions to request = {}", numberOfTransactionsToRequest + ".  (" + (System.currentTimeMillis() - beginningTime) + " ms )");
            log.info("Transactions to request = {}", getNumberOfTransactionsToRequest() + " / " + TransactionViewModel.receivedTransactionCount.get() + " (" + (now - beginningTime)/1000 + " us )");
        }
    }

    public static ScratchpadViewModel instance() {
        return instance;
    }
}
