package com.iota.iri.cache;

import com.iota.iri.cache.impl.CacheManagerImpl;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.storage.Indexable;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

public class CacheManagerTest {

    @Mock
    CacheManager cacheManager;

    @Before
    public void setUp() {
        cacheManager = Mockito.spy(new CacheManagerImpl());
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

    @Test
    public void shouldReturnNullCache() {
        Cache<Indexable, TransactionViewModel> cache = cacheManager.lookup(TransactionViewModel.class);
        Assert.assertNull("Cache should be null", cacheManager.lookup(TransactionViewModel.class));
    }

    @Test
    public void shouldAddCache() {
        cacheManager.add(TransactionViewModel.class);
        Assert.assertNotNull("Cache should be null", cacheManager.lookup(TransactionViewModel.class));
    }
}
