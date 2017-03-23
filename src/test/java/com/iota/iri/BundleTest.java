package com.iota.iri;

import com.iota.iri.hash.Curl;
import com.iota.iri.service.tangle.Tangle;
import com.iota.iri.service.tangle.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.service.viewModels.BundleViewModel;
import com.iota.iri.utils.Converter;
import com.iota.iri.service.viewModels.TransactionViewModel;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Created by paul on 3/5/17 for iri.
 */
public class BundleTest {
    @BeforeClass
    public static void setUp() throws Exception {
        TemporaryFolder dbFolder = new TemporaryFolder();
        dbFolder.create();
        Tangle.instance().addPersistenceProvider(new RocksDBPersistenceProvider());
        Tangle.instance().init(dbFolder.getRoot().getAbsolutePath());
    }

    @AfterClass
    public static void tearDown() throws Exception {
        Tangle.instance().shutdown();
    }

    @Test
    public void getTransactions() throws Exception {
        Random r = new Random();
        byte[] bundleHash = Converter.bytes(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
        TransactionViewModel[] transactionViewModels;
        transactionViewModels = Arrays.stream(new com.iota.iri.model.Transaction[4]).map(t -> {
            com.iota.iri.model.Transaction transaction = new com.iota.iri.model.Transaction();
            transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
            transaction.hash = Converter.bigIntegerValue(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
            transaction.bundle = new com.iota.iri.model.Bundle();
            transaction.bundle.hash = new BigInteger(bundleHash.clone());
            return new TransactionViewModel(transaction);
        }).toArray(TransactionViewModel[]::new);
        {
            com.iota.iri.model.Transaction transaction = new com.iota.iri.model.Transaction();
            transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
            transaction.hash = new BigInteger(bundleHash.clone());
            transaction.bundle = new com.iota.iri.model.Bundle();
            transaction.bundle.hash = new BigInteger(bundleHash.clone());
            transactionViewModels = ArrayUtils.addAll(transactionViewModels, new TransactionViewModel(transaction));
        }
        for(TransactionViewModel transactionViewModel : transactionViewModels) {
            transactionViewModel.store().get();
        }
        BundleViewModel bundle = BundleViewModel.fromHash(transactionViewModels[0].getBundleHash());
        List<List<TransactionViewModel>> transactionList = bundle.getTransactions();
    }

}