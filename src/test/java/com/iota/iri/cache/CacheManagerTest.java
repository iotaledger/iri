package com.iota.iri.cache;

import com.iota.iri.cache.impl.CacheManagerImpl;
import com.iota.iri.conf.DbConfig;
import com.iota.iri.conf.MainnetConfig;
import com.iota.iri.controllers.TransactionViewModel;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Spy;

public class CacheManagerTest {

    @Spy
    CacheManager cacheManager;
    @Spy
    DbConfig dbConfig;

    @Before
    public void setUp() {
        dbConfig = new MainnetConfig();
        cacheManager = new CacheManagerImpl(dbConfig);
    }

    @After
    public void tearDown() {
        cacheManager.clearAllCaches();
    }

    @Test
    public void cacheShouldNotBeNull() {
        Assert.assertNotNull("Cache should not be null", cacheManager.getCache(TransactionViewModel.class));
    }

    @Test
    public void shouldReturnNotNullCache() {
        cacheManager.getCache(TransactionViewModel.class);
        Assert.assertNotNull("Cache should not be null", cacheManager.lookup(TransactionViewModel.class));
    }
}
