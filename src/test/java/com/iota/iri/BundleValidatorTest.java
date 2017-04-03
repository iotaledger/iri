package com.iota.iri;

import com.iota.iri.conf.Configuration;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.Hash;
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

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Created by paul on 3/5/17 for iri.
 */
public class BundleValidatorTest {
    @BeforeClass
    public static void setUp() throws Exception {
        TemporaryFolder dbFolder = new TemporaryFolder();
        TemporaryFolder logFolder = new TemporaryFolder();
        dbFolder.create();
        logFolder.create();
        Configuration.put(Configuration.DefaultConfSettings.DB_PATH, dbFolder.getRoot().getAbsolutePath());
        Configuration.put(Configuration.DefaultConfSettings.DB_LOG_PATH, logFolder.getRoot().getAbsolutePath());
        Tangle.instance().addPersistenceProvider(new RocksDBPersistenceProvider());
        Tangle.instance().init();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        Tangle.instance().shutdown();
    }

    @Test
    public void getTransactions() throws Exception {
        Random r = new Random();
        Hash bundleHash = new Hash(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
        TransactionViewModel[] transactionViewModels;
        transactionViewModels = Arrays.stream(new com.iota.iri.model.Transaction[4]).map(t -> {
            com.iota.iri.model.Transaction transaction = new com.iota.iri.model.Transaction();
            transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
            transaction.hash = new Hash(Arrays.stream(new int[Curl.HASH_LENGTH]).map(i -> r.nextInt(3)-1).toArray());
            transaction.bundle = new com.iota.iri.model.Bundle();
            transaction.bundle.hash = bundleHash;
            return new TransactionViewModel(transaction);
        }).toArray(TransactionViewModel[]::new);
        {
            com.iota.iri.model.Transaction transaction = new com.iota.iri.model.Transaction();
            transaction.bytes = Converter.bytes(Arrays.stream(new int[TransactionViewModel.TRINARY_SIZE]).map(i -> r.nextInt(3)-1).toArray());
            transaction.hash = bundleHash;
            transaction.bundle = new com.iota.iri.model.Bundle();
            transaction.bundle.hash = bundleHash;
            transactionViewModels = ArrayUtils.addAll(transactionViewModels, new TransactionViewModel(transaction));
        }
        for(TransactionViewModel transactionViewModel : transactionViewModels) {
            transactionViewModel.store();
        }
        BundleValidator bundleValidator = new BundleValidator(BundleViewModel.fromHash(transactionViewModels[0].getBundleHash()));
        List<List<TransactionViewModel>> transactionList = bundleValidator.getTransactions();
    }

}