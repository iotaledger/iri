package com.iota.iri.service.viewModels;

import com.iota.iri.model.Address;
import com.iota.iri.model.Hash;
import com.iota.iri.service.tangle.Tangle;
import org.apache.commons.lang3.ArrayUtils;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/**
 * Created by paul on 3/6/17 for iri.
 */
public class AddressViewModel {
    public final Address address;
    public AddressViewModel (BigInteger hash) {
        address = new Address();
        address.hash = hash;
    }
    public AddressViewModel (byte[] hash) {
        this(new BigInteger(hash));
    }

    public void addTransaction(TransactionViewModel transactionViewModel) {
        address.transactions = ArrayUtils.addAll(address.transactions, transactionViewModel.getHash());
    }

    public BigInteger[] getTransactionHashes() throws ExecutionException, InterruptedException {
        if(address.transactions == null)
            Tangle.instance().load(address).get();
        return address.transactions;
    }

    public byte[] bytes() { return Hash.padHash(address.hash);}
    public BigInteger getHash() { return address.hash;}
}
