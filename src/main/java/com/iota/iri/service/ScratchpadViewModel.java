package com.iota.iri.service;

import com.iota.iri.model.Flag;

/**
 * Created by paul on 3/8/17 for iri.
 */
public class ScratchpadViewModel {
    public void requestTransaction(byte[] hash) {
    }

    public void clearAnalyzedTransactionFlags() {
    }

    public boolean analyzedTransactionFlag(byte[] hash) {
        return true;
    }

    public boolean setAnalyzedTransactionFlag(byte[] hash) {
        return false;
    }

    public void saveAnalyzedTransactionsFlags() {

    }

    public void loadAnalyzedTransactionsFlags() {

    }

    public Flag[] getAnalyzedTransactionsFlags() {
        return null;
    }

    public Flag[] getAnalyzedTransactionsFlagsCopy() {
        return null;
    }

    public int getNumberOfTransactionsToRequest() {
        return 0;
    }

    public static ScratchpadViewModel instance() {
        return new ScratchpadViewModel();
    }

    public void clearAnalyzedTransactionsFlags() {
    }
}
