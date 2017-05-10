package com.iota.iri;

import com.iota.iri.conf.Configuration;
import com.iota.iri.controllers.HashesViewModel;
import com.iota.iri.hash.Curl;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider;
import com.iota.iri.utils.Converter;
import com.iota.iri.controllers.TransactionViewModel;
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
    }

}