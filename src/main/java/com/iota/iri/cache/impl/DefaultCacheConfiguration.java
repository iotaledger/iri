package com.iota.iri.cache.impl;

import com.iota.iri.cache.CacheConfiguration;

/**
 * Default Implementation of {@link CacheConfiguration}
 */
public class DefaultCacheConfiguration implements CacheConfiguration {

    @Override
    public long getMaxSize() {
        return DEFAULT_MAX_SIZE;
    }

    @Override
    public int getReleaseCount() {
        return DEFAULT_RELEASE_COUNT;
    }

    @Override
    public int getConcurrencyLevel() {
        return DEFAULT_CONCURRENCY_LEVEL;
    }
}
