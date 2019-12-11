package com.iota.iri.cache;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Cache operations
 * 
 * @param <K> Key type
 * @param <V> Value type
 */
public interface Cache<K, V> {

    /**
     * Get the value mapped to the specified key.
     * If it's not foudn, it reads from DB and updates cache.
     * 
     * @param key The keys whos value is to be returned
     * @return The mapped value of the specified key
     */
    V get(K key);

    /**
     * Gets the value if it is in cache. Does not read from DB if not found.
     * @param key The key
     * @return The cached value.
     */
    V lookup(K key);

    /**
     * Get all elements from the cache for the keys specified.
     * 
     * @param keys The keys to get values of
     * @return A map of key and value associations.
     */
    Map<K, V> getAll(Collection<K> keys);

    /**
     * Get the all keys of the cache
     * 
     * @return A collection of keys in the cache
     */
    List<K> getKeys();

    /**
     * The size of the cache
     * 
     * @return The size of the cache
     */
    int getSize();

    /**
     * Associates the specified value with the specified key in the cache. If the cache already contains a value of this
     * key, the old value will be replaced with this value.
     * 
     * @param key   They key to associate the value with
     * @param value The value to associate the key with
     */
    void put(K key, V value);

    /**
     * Associates the specified value with the specified key if the key does not already exist in the cache If the key
     * already exists, the existing value is returned.
     * 
     * @param key   The key to associate the specified value with
     * @param value The value to associate the specified key with
     */
    void putIfAbsent(K key, V value);

    /**
     * Evict the specified key from the cache
     * 
     * @param key
     */
    void evict(K key);

    /**
     * Evict expired items from the cache according to its {@link CacheConfiguration}.
     */
    void evict();

    /**
     * Evict all items specified in the given collection
     */
    void evict(List<K> keys);

    /**
     * Clear the cache by removing all mappings
     */
    void clear();

    /**
     * The number of cache hits
     * 
     * @return The number of cache hits
     */
    int getCacheHits();

    /**
     * The number of cache misses
     * 
     * @return The number of cache misses
     */
    int getCacheMisses();

    /**
     * Gets the cache configuration being used
     * 
     * @return Cached configuration
     */
    CacheConfiguration getConfiguration();

    /**
     * Poll and return the next key to evict
     * 
     * @return The key to evict
     */
    K nextEvictionKey();
}
