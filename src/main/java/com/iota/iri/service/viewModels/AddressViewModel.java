package com.iota.iri.service.viewModels;

import com.iota.iri.model.Address;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tangle.Tangle;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/**
 * Created by paul on 3/6/17 for iri.
 */
public class AddressViewModel {
    public final Address address;
    public AddressViewModel (byte[] hash) {
        address = new Address();
        address.hash = hash;
    }

    public void addTransaction(TransactionViewModel transactionViewModel) {
        address.transactions = ArrayUtils.addAll(address.transactions, new Hash(transactionViewModel.getHash()));
    }

    public Hash[] getTransactionHashes() throws ExecutionException, InterruptedException {
        Tangle.instance().load(address).get();
        return Arrays.stream(address.transactions).toArray(Hash[]::new);
    }

    public Hash getHash() { return new Hash(address.hash);}
}
