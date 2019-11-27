package com.iota.iri.cache;

import java.util.Collection;
import java.util.Map;

/**
 * Cache operations
 * 
 * @param <K> Key type
 * @param <V> Value type
 */
public interface Cache<K, V> {

    /**
     * Get the value mapped to the specified key. Returns {@code null} if the mapping is not found.
     * 
     * @param key The keys whos value is to be returned
     * @return The mapped value of the specified key
     */
    Cache get(K key);

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
    Collection<K> getKeys();

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
     * Puts the list of values in the cache
     * 
     * @param values values to be put in the cache
     */
    void putAll(Collection<V> values);

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
    void evict(Collection<K> keys);

    /**
     * Clear the cache by removing all mappings
     */
    void clear();
}
