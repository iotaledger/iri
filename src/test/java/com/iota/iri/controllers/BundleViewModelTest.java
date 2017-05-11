package com.iota.iri.controllers;

import com.iota.iri.conf.Configuration;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProviderTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Created by paul on 5/2/17.
 */
public class BundleViewModelTest {
    private static final TemporaryFolder dbFolder = new TemporaryFolder();
    private static final TemporaryFolder logFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        dbFolder.create();
        logFolder.create();
        Configuration.put(Configuration.DefaultConfSettings.DB_PATH, dbFolder.getRoot().getAbsolutePath());
        Configuration.put(Configuration.DefaultConfSettings.DB_LOG_PATH, logFolder.getRoot().getAbsolutePath());
        Tangle.instance().addPersistenceProvider(RocksDBPersistenceProviderTest.rocksDBPersistenceProvider);
        Tangle.instance().init();

    }

    @After
    public void tearDown() throws Exception {
        Tangle.instance().shutdown();
        dbFolder.delete();
        logFolder.delete();
    }

    @Test
    public void quietFromHash() throws Exception {

    }

    @Test
    public void fromHash() throws Exception {

    }

    @Test
    public void getTransactionViewModels() throws Exception {

    }

    @Test
    public void quietGetTail() throws Exception {

    }

    @Test
    public void getTail() throws Exception {

    }

}