package com.iota.iri.tangle.rocksDB;

import com.iota.iri.tangle.IPersistenceProvider;
import com.iota.iri.tangle.TangleAccessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by paul on 3/4/17 for iri.
 */
public class RocksDBPersistenceProviderTest {
    @Before
    public void setUp() throws Exception {
        TangleAccessor.instance().setPersistenceProvider(new RocksDBPersistenceProvider());
        TangleAccessor.instance().init();
    }

    @After
    public void tearDown() throws Exception {
        TangleAccessor.instance().shutdown();
    }

    @Test
    public void save() throws Exception {
    }

    @Test
    public void get() throws Exception {

    }

    @Test
    public void query() throws Exception {

    }

}