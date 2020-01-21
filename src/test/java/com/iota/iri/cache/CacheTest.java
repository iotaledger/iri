package com.iota.iri.cache;

import com.iota.iri.cache.impl.CacheImpl;
import com.iota.iri.cache.impl.DefaultCacheConfiguration;
import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.storage.Indexable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

public class CacheTest {

    private static final String TEST_TRANSACTION_HASH = "TEST9TRANSACTION9TEST9TRANSACTION9TEST9TRANSACTION9TEST9TRANSACTION9TEST999999999";
    private static final String TEST_TRANSACTION_HASH1 = "A9999TRANSACTION9TEST9TRANSACTION9TEST9TRANSACTION9TEST9TRANSACTION9TEST999999999";
    private static final Hash hash = HashFactory.TRANSACTION.create(TEST_TRANSACTION_HASH);
    private static final Hash hash1 = HashFactory.TRANSACTION.create(TEST_TRANSACTION_HASH1);

    @Mock
    private Cache<Indexable, TransactionViewModel> cache;

    @Before
    public void setUp() {
        cache = Mockito.spy(new CacheImpl<>(new DefaultCacheConfiguration()));
    }

    @After
    public void tearDown() {
        cache.clear();
    }

    @Test
    public void shouldGetCachedValue() {
        TransactionViewModel tvm = new TransactionViewModel(getTransaction(TEST_TRANSACTION_HASH), hash);
        cache.put(hash, tvm);

        Assert.assertEquals("Tranasction view models should be equal", tvm, cache.get(hash));
    }

    @Test
    public void shouldGetAll() {
        List<Indexable> keys = new ArrayList<Indexable>() {

            {
                add(hash);
            }
        };

        TransactionViewModel tvm = new TransactionViewModel(getTransaction(TEST_TRANSACTION_HASH), hash);
        Map<Indexable, TransactionViewModel> values = new HashMap<Indexable, TransactionViewModel>() {

            {
                put(hash, tvm);
            }
        };

        cache.put(hash, tvm);

        Assert.assertTrue("Values map should not be empty", !values.isEmpty());
        Assert.assertEquals("TVMs should be equals", tvm, cache.getAll(keys).get(hash));
    }

    @Test
    public void sizeShouldBeOne() {
        cache.put(hash, new TransactionViewModel(getTransaction(TEST_TRANSACTION_HASH), hash));

        Assert.assertTrue("Cache size should be 1", cache.getSize() == 1);
    }

    @Test
    public void shouldPutKeyInCache() {
        cache.put(hash, new TransactionViewModel(getTransaction(TEST_TRANSACTION_HASH), hash));
        Assert.assertTrue("Cache should not be empty", cache.getSize() > 0);
    }

    @Test
    public void shouldEvictTheItemWithTheSpecifiedKey() {
        cache.put(hash, new TransactionViewModel(getTransaction(TEST_TRANSACTION_HASH), hash));
        cache.put(hash1, new TransactionViewModel(getTransaction(TEST_TRANSACTION_HASH1), hash1));

        cache.release(hash);

        Assert.assertTrue("Cache should only have 1 element left", cache.getSize() == 1);
        Assert.assertEquals("Remaining item should be hash1", cache.get(hash1).getHash(), hash1);
    }

    @Test
    public void shouldEvict() {
        cache.put(hash, new TransactionViewModel(getTransaction(TEST_TRANSACTION_HASH), hash));
        cache.release();
        Assert.assertTrue("Cache should be empty after full eviction", cache.getSize() == 0);
    }

    @Test
    public void shouldEvictElementsWithKeys() {
        cache.put(hash, new TransactionViewModel(getTransaction(TEST_TRANSACTION_HASH), hash));
        cache.put(hash1, new TransactionViewModel(getTransaction(TEST_TRANSACTION_HASH1), hash1));

        List<Indexable> keys = new ArrayList<Indexable>() {

            {
                add(hash);
                add(hash1);
            }
        };

        cache.release(keys);

        Assert.assertTrue("Cache should be empty", cache.getSize() == 0);
    }

    @Test
    public void shouldClearCache() {
        cache.put(hash, new TransactionViewModel(getTransaction(TEST_TRANSACTION_HASH), hash));
        cache.put(hash1, new TransactionViewModel(getTransaction(TEST_TRANSACTION_HASH1), hash1));
        cache.clear();

        Assert.assertTrue("Cache should be empty", cache.getSize() == 0);
    }

    @Test
    public void shouldReturn1CacheMiss() {
        cache.put(hash, new TransactionViewModel(getTransaction(TEST_TRANSACTION_HASH), hash));
        cache.get(hash);

        Assert.assertTrue("Cache Hits should be 1", cache.getCacheHits() == 1);
        Assert.assertTrue("Cache Misses should be 0", cache.getCacheMisses() == 0);
    }

    @Test
    public void shouldReturnOneCacheMiss() {
        cache.put(hash, new TransactionViewModel(getTransaction(TEST_TRANSACTION_HASH), hash));
        cache.get(hash1);

        Assert.assertTrue("Cache Hits should be 0", cache.getCacheHits() == 0);
        Assert.assertTrue("Cache Misses should be 1", cache.getCacheMisses() == 1);
    }

    private Transaction getTransaction(String hash) {
        Transaction tx = new Transaction();
        tx.address = HashFactory.TRANSACTION.create(hash);
        return tx;
    }

}
