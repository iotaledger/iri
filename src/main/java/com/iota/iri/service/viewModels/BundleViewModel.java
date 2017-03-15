package com.iota.iri.service.viewModels;

import com.iota.iri.model.Bundle;
import com.iota.iri.service.tangle.Tangle;

import java.util.concurrent.ExecutionException;

/**
 * Created by paul on 3/15/17 for iri-testnet.
 */
public class BundleViewModel {
    private final Bundle bundle;

    public static BundleViewModel fromHash(byte[] hash) throws ExecutionException, InterruptedException {
        Bundle bundle = (Bundle) Tangle.instance().load(Bundle.class, hash).get();
        return new BundleViewModel(bundle);
    }
    public BundleViewModel(Bundle bundle) {
        this.bundle = bundle;
    }

    public TransactionViewModel[] getTransactions() throws Exception {
        if(this.bundle.transactions == null) {
            this.bundle.transactions = ((Bundle) Tangle.instance().load(Bundle.class, bundle.hash).get()).transactions;
        }
        TransactionViewModel[] transactionViewModels = new TransactionViewModel[this.bundle.transactions.length + 1];
        for(int i = 0; i < bundle.transactions.length; i++) {
            transactionViewModels[i] = TransactionViewModel.fromHash(this.bundle.transactions[i].hash);
        }
        transactionViewModels[transactionViewModels.length-1] = TransactionViewModel.fromHash(bundle.hash);
        return transactionViewModels;
    }
}
