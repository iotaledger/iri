package com.iota.iri.service;

import com.iota.iri.Milestone;
import com.iota.iri.model.Flag;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Scratchpad;
import com.iota.iri.model.Transaction;
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

    private ByteBuffer transactionsToRequest;
    private ByteBuffer analyzedTransactionsFlags, analyzedTransactionsFlagsCopy;

    private final byte[] transactionToRequest = new byte[TransactionViewModel.HASH_SIZE];
    private final Object transactionToRequestMonitor = new Object();
    private int previousNumberOfTransactions;

    protected static final byte[] ZEROED_BUFFER = new byte[CELL_SIZE];
    public volatile int numberOfTransactionsToRequest;

    static long lastTime = 0L;

    public static ScratchpadViewModel instance;

    Set<byte[]> analyzedTransactions = new TreeSet<>();

    public void requestTransaction(byte[] hash) {
        Scratchpad scratchpad = new Scratchpad();
        scratchpad.hash = hash;
        Tangle.instance().save(scratchpad);
        numberOfTransactionsToRequest++;
    }

    public boolean getAnalyzedTransactionFlag(byte[] hash) {
        return analyzedTransactions.contains(hash);
    }

    public boolean setAnalyzedTransactionFlag(byte[] hash) {
        return analyzedTransactions.add(hash);
    }

    public Flag[] getAnalyzedTransactionsFlags() {
        return analyzedTransactions.stream().map(b -> new Flag(b)).toArray(Flag[]::new);
    }

    public int getNumberOfTransactionsToRequest() {
        return numberOfTransactionsToRequest;
    }

    public static ScratchpadViewModel instance() {
        return instance;
    }

    public void clearAnalyzedTransactionsFlags() {
        analyzedTransactionsFlags.position(0);
        for (int i = 0; i < ANALYZED_TRANSACTIONS_FLAGS_SIZE / CELL_SIZE; i++) {
            analyzedTransactionsFlags.put(ZEROED_BUFFER);
        }
    }

    public void clearReceivedTransaction(byte[] hash) {
        Scratchpad scratchpad = new Scratchpad();
        scratchpad.hash = hash;
        Tangle.instance().delete(scratchpad);
    }

    public void transactionToRequest(byte[] buffer, int offset) {
        Scratchpad scratchpad = null;
        try {
            scratchpad = ((Scratchpad) Tangle.instance().getLatest(Scratchpad.class).get());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        if(scratchpad != null) {
            TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(scratchpad.hash);
            if(Arrays.equals(transactionViewModel.getBytes(), TransactionViewModel.NULL_TRANSACTION_BYTES)) {
                System.arraycopy(transactionViewModel.getHash(), 0, buffer, offset, TransactionViewModel.HASH_SIZE);
            }
        }
    }
}
