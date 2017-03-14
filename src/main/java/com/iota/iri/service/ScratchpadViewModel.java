package com.iota.iri.service;

import com.iota.iri.model.*;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.service.viewModels.TransactionViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;

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
        analyzedTransactionHandle = Tangle.instance().createTransientList(AnalyzedFlag.class);
    }

    public void requestTransaction(byte[] hash) throws ExecutionException, InterruptedException {
        Scratchpad scratchpad = new Scratchpad();
        scratchpad.hash = hash;
        scratchpad.value = 1;
        Tangle.instance().save(scratchpad).get();
        numberOfTransactionsToRequest++;
    }

    public boolean setAnalyzedTransactionFlag(byte[] hash) throws ExecutionException, InterruptedException {
        AnalyzedFlag flag = new AnalyzedFlag();
        flag.hash = hash;
        flag.bytes = new byte[]{0};
        return Tangle.instance().save(analyzedTransactionHandle, flag).get();
    }

    public void clearAnalyzedTransactionsFlags() throws Exception {
        Tangle.instance().dropList(analyzedTransactionHandle);
        analyzedTransactionHandle = Tangle.instance().createTransientList(AnalyzedFlag.class);
    }

    public int getNumberOfTransactionsToRequest() {
        return numberOfTransactionsToRequest;
    }

    public void clearReceivedTransaction(byte[] hash) throws ExecutionException, InterruptedException {
        Scratchpad scratchpad = new Scratchpad();
        scratchpad.hash = hash;
        if(Tangle.instance().maybeHas(Scratchpad.class, hash).get()) {
            Tangle.instance().delete(scratchpad);
            numberOfTransactionsToRequest--;
        }
    }

    public void transactionToRequest(byte[] buffer, int offset) throws ExecutionException, InterruptedException {
        Scratchpad scratchpad = null;
        final long beginningTime = System.currentTimeMillis();
        Object latest = Tangle.instance().getLatest(Scratchpad.class).get();
        if(latest != null)
            scratchpad = ((Scratchpad) latest);

        if(scratchpad != null && !Arrays.equals(scratchpad.hash, TransactionViewModel.NULL_TRANSACTION_HASH_BYTES)) {
            System.arraycopy(scratchpad.hash, 0, buffer, offset, TransactionViewModel.HASH_SIZE);
        }
        long now = System.currentTimeMillis();
        if ((now - lastTime) > 10000L) {
            lastTime = now;
            //log.info("Transactions to request = {}", numberOfTransactionsToRequest + ".  (" + (System.currentTimeMillis() - beginningTime) + " ms )");
            log.info("Transactions to request = {}", numberOfTransactionsToRequest + " / " + TransactionViewModel.receivedTransactionCount.get() + " (" + (System.currentTimeMillis() - beginningTime) + " ms )");
        }
    }

    public static ScratchpadViewModel instance() {
        return instance;
    }
}
