package com.iota.iri;

import com.iota.iri.hash.Curl;
import com.iota.iri.tangle.TangleAccessor;
import com.iota.iri.tangle.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Converter;
import com.iota.iri.viewModel.Transaction;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Created by paul on 3/5/17 for iri.
 */
public class BundleTest {
    @Test
    public void getTransactions() throws Exception {
        TangleAccessor.instance().setPersistenceProvider(new RocksDBPersistenceProvider());
        TangleAccessor.instance().init();
        Random r = new Random();
        byte[] bundleHash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
        Transaction[] transactions;
        transactions = Arrays.stream(new com.iota.iri.model.Transaction[4]).map(t -> {
            com.iota.iri.model.Transaction transaction = new com.iota.iri.model.Transaction();
            transaction.bytes = Converter.bytes(Arrays.stream(new int[com.iota.iri.viewModel.Transaction.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
            transaction.hash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
            transaction.bundle = bundleHash.clone();
            return new Transaction(transaction);
        }).toArray(Transaction[]::new);
        {
            com.iota.iri.model.Transaction transaction = new com.iota.iri.model.Transaction();
            transaction.bytes = Converter.bytes(Arrays.stream(new int[com.iota.iri.viewModel.Transaction.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
            transaction.hash = bundleHash.clone();
            transaction.bundle = bundleHash.clone();
            transactions = ArrayUtils.addAll(transactions, new Transaction(transaction));
        }
        for(Transaction transaction: transactions) {
            transaction.store().get();
        }
        Bundle bundle = new Bundle(transactions[0].getBundleHash());
        List<List<Transaction>> transactionList = bundle.getTransactions();
    }

}