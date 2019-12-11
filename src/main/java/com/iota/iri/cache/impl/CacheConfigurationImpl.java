package com.iota.iri.cache.impl;

import com.iota.iri.cache.CacheConfiguration;

/**
 * Implementation of {@link CacheConfiguration}
 */
public class CacheConfigurationImpl implements CacheConfiguration {

    @Override
    public long getMaxSize() {
        return DEFAULT_MAX_SIZE;
    }

    @Override
    public int getEvictionCount() {
        return DEFAULT_EVICTION_COUNT;
    }
}
