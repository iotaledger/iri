package com.iota.iri.cache;

import com.iota.iri.storage.Indexable;

/**
 * Cache Manager
 */
public interface CacheManager {

    /**
     * Get the cache with the specified value type. If the cache is not found, it will be created
     * 
     * @param type Value type
     * @param <V>  Template type for cache value
     * @return The cache found.
     */
    <V> Cache<Indexable, V> getCache(Class<V> type);

    /**
     * Check if the cache with the specified valye type eixsts. If it does not exists, returns {@code null}
     * 
     * @param type Value type
     * @param <V>  Template type for cache value
     * @return Cache if found. Otherwise, {@code null}
     */
    <V> Cache<Indexable, V> lookup(Class<V> type);

    /**
     * Adds a new cache of the specified type. If a cache with the specified types already exists, the existing one will
     * be returned. Otherwise, created.
     *
     * @param type The type of the cache
     * @param <V>  Template type for cache value
     * @return The new cache created.
     */
    <V> Cache add(Class<V> type);

    /**
     * Evicts all items in all caches
     */
    void clearAllCaches();
}
