package com.iota.iri.service.tangle.rocksDB;

import com.iota.iri.model.Flag;
import com.iota.iri.service.tangle.Tangle;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Created by paul on 3/4/17 for iri.
 */
public class RocksDBPersistenceProviderTest {

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
}