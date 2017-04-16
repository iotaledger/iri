package com.iota.iri.controllers;

import com.iota.iri.model.Address;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;
import org.apache.commons.lang3.ArrayUtils;

import java.util.concurrent.ExecutionException;

/**
 * Created by paul on 3/6/17 for iri.
 */
public class AddressViewModel {
    private final Address address;
    public AddressViewModel (Hash hash) {
        address = new Address();
        address.hash = hash;
    }

    public void addTransaction(TransactionViewModel transactionViewModel) {
        address.transactions = ArrayUtils.addAll(address.transactions, transactionViewModel.getHash());
    }

    public Hash[] getTransactionHashes() throws ExecutionException, InterruptedException {
        if(address.transactions == null)
            Tangle.instance().load(address).get();
        return address.transactions;
    }

    public byte[] bytes() { return address.hash.bytes();}
    public Hash getHash() { return address.hash;}
}
