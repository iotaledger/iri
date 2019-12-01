package com.iota.iri.cache;

/**
 * Configuration for instantiating a cache
 */
public interface CacheConfiguration {

    long DEFAULT_MAX_SIZE = 256;
    int DEFAULT_EVICTION_COUNT = 10;
    boolean DEFAULT_WEAK_REFERENCE = false;
    CacheEvictionPolicy DEFAULT_EVICTION_POLICY = CacheEvictionPolicy.FIFO;
    boolean DEFAULT_IS_NULL_ALLOWED = false;
    int DEFAULT_TIME_TO_IDLE_SECONDS = 0;

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

    /**
     * Determines if the cache should use weak/soft reference to values or not
     * 
     * @return True if weak references are used. Otherwise, false.
     */
    boolean isWeakReference();

    /**
     * Gets the {@link CacheEvictionPolicy}
     * 
     * @return The cache eviction policy
     */
    CacheEvictionPolicy getEvictionPolicy();

    /**
     * The maximum number of seconds an element can exist in cache without it being accessed. Idle elements will not be
     * returned. The default value is 0, which means there's no TTL on this cache
     *
     * @return The time to idle
     */
    int getTimeToIdleSeconds();

    /**
     * Determines if null values are allowed in the cache
     * 
     * @return True if null allowed. Otherwise, false.
     */
    boolean isNullAllowed();
}
