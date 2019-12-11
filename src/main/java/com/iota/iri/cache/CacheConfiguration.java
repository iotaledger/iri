package com.iota.iri.cache;

/**
 * Configuration for instantiating a cache
 */
public interface CacheConfiguration {

    long DEFAULT_MAX_SIZE = 1000;
    int DEFAULT_EVICTION_COUNT = 10;

    /**
     * Gets the max size of the cache
     * 
     * @return The max size
     */
    long getMaxSize();

    /**
     * Gets the number of items to evict in a single batch of eviction
     * 
     * @return eviction count
     */
    int getEvictionCount();
}
