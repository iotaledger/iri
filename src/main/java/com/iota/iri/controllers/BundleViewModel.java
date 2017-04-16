package com.iota.iri.controllers;

import com.iota.iri.model.Bundle;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;

import java.util.Arrays;

/**
 * Created by paul on 3/15/17 for iri-testnet.
 */
public class BundleViewModel {
    private final Bundle bundle;
    private TransactionViewModel[] transactionViewModels;

    public static BundleViewModel quietFromHash(Hash hash) {
        try {
            return fromHash(hash);
        } catch (Exception e) {
            return new BundleViewModel(null);
        }
    }

    public static BundleViewModel fromHash(Hash hash) throws Exception {
        Bundle bundle = new Bundle();
        bundle.hash = hash;
        Tangle.instance().load(bundle).get();
        return new BundleViewModel(bundle);
    }

    private BundleViewModel(Bundle bundle) {
        this.bundle = bundle == null ? new Bundle() : bundle;
    }

    public TransactionViewModel[] getTransactionViewModels() throws Exception {
        if (this.bundle.transactions == null) {
            Tangle.instance().load(bundle).get();
            if(bundle.transactions == null) {
                bundle.transactions = new Hash[0];
            }
        }
        if(transactionViewModels == null) {

            transactionViewModels = new TransactionViewModel[this.bundle.transactions.length];// + 1];
            for (int i = 0; i < bundle.transactions.length; i++) {
                transactionViewModels[i] = TransactionViewModel.fromHash(this.bundle.transactions[i]);
            }
        }
        return transactionViewModels;
    }
    public TransactionViewModel quietGetTail() {
        try {
            return getTail();
        } catch (Exception e) {
            return null;
        }
    }
    public TransactionViewModel getTail() throws Exception {
        getTransactionViewModels();
        return Arrays.stream(transactionViewModels).filter(t -> t.getCurrentIndex() == 0).findFirst().orElse(null);
    }
}
