package com.iota.iri.service.viewModels;

import com.iota.iri.model.Address;
import com.iota.iri.model.Hash;
import com.iota.iri.model.Transaction;
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
        address.bytes = hash;
    }

    public void addTransaction(TransactionViewModel transactionViewModel) {
        Transaction transaction = new Transaction();
        transaction.hash = transactionViewModel.getHash();
        address.transactions = ArrayUtils.addAll(address.transactions, transaction);
    }

    public Hash[] getTransactionHashes() {
        try {
            Address txAddress = (Address) Tangle.instance().load(Address.class, address.bytes).get();
            if(txAddress != null && address.transactions != null) {
                address.transactions = txAddress.transactions.clone();
            } else {
                address.transactions = new Transaction[0];
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return Arrays.stream(address.transactions).map(transaction -> new Hash(transaction.hash)).toArray(Hash[]::new);
    }

    public Hash getHash() { return new Hash(address.bytes);}
}
