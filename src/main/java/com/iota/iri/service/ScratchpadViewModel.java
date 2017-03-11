package com.iota.iri.service;

import com.iota.iri.model.Flag;

import java.util.Set;
import java.util.TreeSet;

/**
 * Created by paul on 3/8/17 for iri.
 */
public class ScratchpadViewModel {
    public static ScratchpadViewModel instance;

    Set<byte[]> analyzedTransactions = new TreeSet<>();
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

    public void transactionToRequest(byte[] data, int size) {
    }
}
