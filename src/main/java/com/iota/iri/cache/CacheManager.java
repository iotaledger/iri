package com.iota.iri.cache;

/**
 * Cache Manager
 */
public interface CacheManager {

    /**
     * Get the cache with the specified value type. If the cache is not found, it will be created
     * 
     * @param type Value type
     * @param <T>  Template
     * @return The cache found.
     */
    <T> Cache getCache(Class<T> type);

    /**
     * Check if the cache with the specified valye type eixsts. If it does not exists, returns {@code null}
     * 
     * @param type Value type
     * @param <T>  Template
     * @return Cache if found. Otherwise, {@code null}
     */
    <T> Cache lookup(Class<T> type);

    /**
     * Adds a new cache of the specified type. If a cache with the specified types already exists, the existing one will
     * be returned. Otherwise, created.
     *
     * @param type The type of the cache
     * @param <T>  Template
     * @return The new cache created.
     */
    <T> Cache add(Class<T> type);
}
