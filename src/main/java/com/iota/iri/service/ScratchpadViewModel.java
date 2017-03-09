package com.iota.iri.service;

import com.iota.iri.model.Flag;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by paul on 3/8/17 for iri.
 */
public class ScratchpadViewModel {
    public static ScratchpadViewModel instance;

    List<byte[]> analyzedTransactions = new ArrayList<>();
    int numberOfTransactionsToRequest = 0;

    public void requestTransaction(byte[] hash) {
        numberOfTransactionsToRequest++;
    }

    public boolean analyzedTransactionFlag(byte[] hash) {
        return analyzedTransactions.contains(hash);
    }

    public boolean setAnalyzedTransactionFlag(byte[] hash) {
        return analyzedTransactions.add(hash);
    }

    public void saveAnalyzedTransactionsFlags() {
    }

    public void loadAnalyzedTransactionsFlags() {

    }

    public Flag[] getAnalyzedTransactionsFlags() {
        return analyzedTransactions.stream().map(b -> new Flag(b)).toArray(Flag[]::new);
    }

    public Flag[] getAnalyzedTransactionsFlagsCopy() {
        return getAnalyzedTransactionsFlags();
    }

    public int getNumberOfTransactionsToRequest() {
        return numberOfTransactionsToRequest;
    }

    public static ScratchpadViewModel instance() {
        return instance;
    }

    public void clearAnalyzedTransactionsFlags() {
        analyzedTransactions.clear();
    }
}
