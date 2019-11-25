package com.iota.iri.cache;

import java.util.Collection;

/**
 * Cache Manager
 */
public interface CacheManager {

    /**
     * Get the names of all caches
     * 
     * @return A collection of cache names
     */
    Collection<String> getCacheNames();

    /**
     * Get the cache with the specified name
     * 
     * @param name The name of the cache to get
     * @return The cache with the specified name
     */
    Cache getCache(String name);

    /**
     * Check if a cache exists. If it exists, returns it. Else, returns null
     * 
     * @param name The name of the cache to check
     * @return The cache if found.
     */
    Cache lookup(String name);

    /**
     * Adds a new cache to this cache manager
     * 
     * @param cache The cache to add
     */
    void add(Cache cache);
}
