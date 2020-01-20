package com.iota.iri.cache.impl;

import com.iota.iri.cache.CacheConfiguration;

/**
 * Default Implementation of {@link CacheConfiguration}
 */
public class CacheConfigurationImpl implements CacheConfiguration {

    private long maxSize;
    private int evictionCount;

    @Override
    public long getMaxSize() {
        return maxSize;
    }

    @Override
    public int getEvictionCount() {
        return evictionCount;
    }

    @Override
    public int getConcurrencyLevel() {
        return DEFAULT_CONCURRENCY_LEVEL;
    }

    /**
     * Constructor for a cache Implementation.
     *
     * @param maxSize       The max size of the cache
     * @param evictionCount The number of items to evict
     */
    public CacheConfigurationImpl(long maxSize, int evictionCount) {
        this.maxSize = maxSize;
        this.evictionCount = evictionCount;
    }
}