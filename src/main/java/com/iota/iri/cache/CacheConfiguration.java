package com.iota.iri.cache;

/**
 * Configuration for instantiating a cache
 */
public interface CacheConfiguration {

    int DEFAULT_CONCURRENCY_LEVEL = 5;

    /**
     * Gets the max size of the cache
     * 
     * @return The max size
     */
    long getMaxSize();

    /**
     * Gets the concurrency level of the cache amp
     * 
     * @return concurrency level
     */
    int getConcurrencyLevel();
}
