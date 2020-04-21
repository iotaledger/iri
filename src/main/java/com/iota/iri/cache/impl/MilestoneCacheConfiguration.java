package com.iota.iri.cache.impl;

/**
 * Cache Configuration for milestones
 */
public class MilestoneCacheConfiguration extends DefaultCacheConfiguration {

    private static final int MAX_SIZE = 30;
    private static final int EVICTION_COUNT = MAX_SIZE;

    @Override
    public long getMaxSize() {
        return MAX_SIZE;
    }

    @Override
    public int getEvictionCount() {
        return EVICTION_COUNT;
    }
}
