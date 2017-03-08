package com.iota.iri;

import com.iota.iri.hash.Curl;
import com.iota.iri.tangle.Tangle;
import com.iota.iri.tangle.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Converter;
import com.iota.iri.viewModel.TransactionViewModel;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Created by paul on 3/5/17 for iri.
 */
public class BundleTest {
    @Test
    public void getTransactions() throws Exception {
        Tangle.instance().addPersistenceProvider(new RocksDBPersistenceProvider());
        Tangle.instance().init();
        Random r = new Random();
        byte[] bundleHash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
        TransactionViewModel[] transactionViewModels;
        transactionViewModels = Arrays.stream(new com.iota.iri.model.Transaction[4]).map(t -> {
            com.iota.iri.model.Transaction transaction = new com.iota.iri.model.Transaction();
            transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
            transaction.hash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
            transaction.bundle.hash = bundleHash.clone();
            return new TransactionViewModel(transaction);
        }).toArray(TransactionViewModel[]::new);
        {
            com.iota.iri.model.Transaction transaction = new com.iota.iri.model.Transaction();
            transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
            transaction.hash = bundleHash.clone();
            transaction.bundle.hash = bundleHash.clone();
            transactionViewModels = ArrayUtils.addAll(transactionViewModels, new TransactionViewModel(transaction));
        }
        for(TransactionViewModel transactionViewModel : transactionViewModels) {
            transactionViewModel.store().get();
        }
        Bundle bundle = new Bundle(transactionViewModels[0].getBundleHash());
        List<List<TransactionViewModel>> transactionList = bundle.getTransactions();
    }

}