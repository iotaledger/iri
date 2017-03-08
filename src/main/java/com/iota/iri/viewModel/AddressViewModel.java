package com.iota.iri.viewModel;

import com.iota.iri.model.Address;
import com.iota.iri.model.Hash;
import com.iota.iri.tangle.Tangle;

import java.util.Arrays;

/**
 * Created by paul on 3/6/17 for iri.
 */
public class AddressViewModel {
    public final Address address;
    public AddressViewModel (byte[] hash) {
        address = new Address();
        address.bytes = hash;
    }

    public void addTransaction(TransactionViewModel transactionViewModel) {
        //address.transactions = ArrayUtils.addAll(address.transactions, new Hash(transactionViewModel.getHash()));
    }

    public Hash[] getTransactionHashes() {
        Tangle.instance().load(address, address.bytes);
        return Arrays.stream(address.transactions).map(transaction -> new Hash(transaction.hash)).toArray(Hash[]::new);
    }

    public Hash getHash() { return new Hash(address.bytes);}
}
