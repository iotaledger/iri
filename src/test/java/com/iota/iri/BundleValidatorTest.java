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
    private static Tangle tangle = new Tangle();

    @BeforeClass
    public static void setUp() throws Exception {
        TemporaryFolder dbFolder = new TemporaryFolder();
        TemporaryFolder logFolder = new TemporaryFolder();
        dbFolder.create();
        logFolder.create();
        tangle.addPersistenceProvider(new RocksDBPersistenceProvider(dbFolder.getRoot().getAbsolutePath(), logFolder.getRoot().getAbsolutePath()));
        tangle.init();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        tangle.shutdown();
    }

    @Test
    public void getTransactions() throws Exception {
    }

}