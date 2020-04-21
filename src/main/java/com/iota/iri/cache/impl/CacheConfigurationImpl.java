package com.iota.iri.cache.impl;

import com.iota.iri.cache.CacheConfiguration;

/**
 * Default Implementation of {@link CacheConfiguration}
 */
public class CacheConfigurationImpl implements CacheConfiguration {

    private long maxSize;
    private int releaseCount;

    @Override
    public long getMaxSize() {
        return maxSize;
    }

    @Override
    public int getReleaseCount() {
        return releaseCount;
    }

    @Override
    public int getConcurrencyLevel() {
        return DEFAULT_CONCURRENCY_LEVEL;
    }

    /**
     * Constructor for a cache Implementation.
     *
     * @param maxSize      The max size of the cache
     * @param releaseCount The number of items to release
     */
    public CacheConfigurationImpl(long maxSize, int releaseCount) {
        this.maxSize = maxSize;
        this.releaseCount = releaseCount;
    }
}