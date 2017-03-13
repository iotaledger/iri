package com.iota.iri.service;

import com.iota.iri.Milestone;
import com.iota.iri.model.*;
import com.iota.iri.service.storage.AbstractStorage;
import com.iota.iri.service.storage.StorageTransactions;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.service.viewModels.TransactionViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
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

    public void requestTransaction(byte[] hash) {
        Scratchpad scratchpad = new Scratchpad();
        scratchpad.hash = hash;
        scratchpad.bytes = new byte[]{1};
        try {
            Tangle.instance().save(scratchpad).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        numberOfTransactionsToRequest++;
    }

    public boolean setAnalyzedTransactionFlag(byte[] hash) {
        AnalyzedFlag flag = new AnalyzedFlag();
        flag.hash = hash;
        flag.bytes = new byte[]{0};
        try {
            return Tangle.instance().save(analyzedTransactionHandle, flag).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void clearAnalyzedTransactionsFlags() {
        try {
            Tangle.instance().dropList(analyzedTransactionHandle);
        } catch (Exception e) {
            e.printStackTrace();
        }
        analyzedTransactionHandle = Tangle.instance().createTransientList(AnalyzedFlag.class);
    }

    public int getNumberOfTransactionsToRequest() {
        return numberOfTransactionsToRequest;
    }

    public void clearReceivedTransaction(byte[] hash) {
        Scratchpad scratchpad = new Scratchpad();
        scratchpad.hash = hash;
        try {
            if(Tangle.instance().maybeHas(Scratchpad.class, hash).get())
                Tangle.instance().delete(scratchpad);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public void transactionToRequest(byte[] buffer, int offset) {
        Scratchpad scratchpad = null;
        try {
            Object latest = Tangle.instance().getLatest(Scratchpad.class).get();
            if(latest != null)
                scratchpad = ((Scratchpad) latest);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        if(scratchpad != null) {
            /*
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(scratchpad.hash);
            if(transactionViewModel != null && Arrays.equals(transactionViewModel.getBytes(), TransactionViewModel.NULL_TRANSACTION_BYTES)) {
                System.arraycopy(transactionViewModel.getHash(), 0, buffer, offset, TransactionViewModel.HASH_SIZE);
            }
            */
            if(!Arrays.equals(scratchpad.hash, TransactionViewModel.NULL_TRANSACTION_HASH_BYTES)) {
                System.arraycopy(scratchpad.hash, 0, buffer, offset, TransactionViewModel.HASH_SIZE);
            }
        }
    }

    public static ScratchpadViewModel instance() {
        return instance;
    }
}
