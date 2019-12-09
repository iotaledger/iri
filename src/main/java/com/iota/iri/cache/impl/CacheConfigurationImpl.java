package com.iota.iri.cache.impl;

import com.iota.iri.cache.CacheConfiguration;

public class CacheConfigurationImpl implements CacheConfiguration {

    @Override
    public long getMaxSize() {
        return DEFAULT_MAX_SIZE;
    }

    @Override
    public int getEvictionCount() {
        return DEFAULT_EVICTION_COUNT;
    }

    @Override
    public boolean isWeakReference() {
        return DEFAULT_WEAK_REFERENCE;
    }
}
