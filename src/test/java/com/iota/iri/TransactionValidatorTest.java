package com.iota.iri;

import com.iota.iri.conf.Configuration;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.controllers.TransactionViewModelTest;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProviderTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by paul on 5/14/17.
 */
public class TransactionValidatorTest {

    private static TemporaryFolder dbFolder;
    private static TemporaryFolder logFolder;
    Logger log = LoggerFactory.getLogger(TransactionValidatorTest.class);

    private static final Random seed = new Random();

    @Before
    public void setUp() throws Exception {
        dbFolder = new TemporaryFolder();
        logFolder = new TemporaryFolder();
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
    public void init() throws Exception {

    }

    @Test
    public void validate() throws Exception {

    }

    @Test
    public void validate1() throws Exception {

    }

    @Test
    public void validate2() throws Exception {

    }
}