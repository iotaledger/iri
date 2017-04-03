package com.iota.iri.service.viewModels;

import com.iota.iri.hash.Curl;
import com.iota.iri.hash.ISS;
import com.iota.iri.model.Bundle;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.utils.Converter;

import java.util.*;

/**
 * Created by paul on 3/15/17 for iri-testnet.
 */
public class BundleViewModel {
    private final Bundle bundle;
    private TransactionViewModel[] transactionViewModels;

    public static BundleViewModel fromHash(Hash hash) throws Exception {
        Bundle bundle = new Bundle();
        bundle.hash = hash;
        Tangle.instance().load(bundle).get();
        return new BundleViewModel(bundle);
    }

    public BundleViewModel(Bundle bundle) throws Exception {
        this.bundle = bundle;
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
}
