package com.iota.iri.cache.impl;

import com.iota.iri.cache.CacheConfiguration;

/**
 * Default Implementation of {@link CacheConfiguration}
 */
public class CacheConfigurationImpl implements CacheConfiguration {

    private long maxSize;

    /**
     * Constructor for a cache Implementation.
     *
     * @param maxSize The max size of the cache
     */
    public CacheConfigurationImpl(long maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public long getMaxSize() {
        return maxSize;
    }

    @Override
    public int getConcurrencyLevel() {
        return DEFAULT_CONCURRENCY_LEVEL;
    }
}