package com.iota.iri.cache;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Cache operations
 * 
 * @param <K> Key type
 * @param <V> Value type
 */
public interface Cache<K, V> {

    /**
     * Get the value mapped to the specified key. If it's not found, it reads from DB and updates cache.
     * 
     * @param key The keys whose value is to be returned
     * @return The mapped value of the specified key
     */
    V get(K key);

    /**
     * Get all elements from the cache for the keys specified.
     * 
     * @param keys The keys to get values of
     * @return A map of key and value associations.
     */
    Map<K, V> getAll(Collection<K> keys);

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
     * Release the specified key from the cache and puts it in the weak store.
     * 
     * @param key
     */
    void release(K key);

    /**
     * Release expired items from the cache according to its {@link CacheConfiguration} and puts it in the weak store.
     */
    void release();

    /**
     * Release all items specified in the given collection and puts them in the weak store.
     */
    void release(List<K> keys);

    /**
     * Permanently deletes an item from cache. It does not put it in the weak store.
     * 
     * @param key The key to delete
     */
    void delete(K key);

    /**
     * Permanently deltes a list of items from cache. It does not put them in the weak store.
     * 
     * @param keys The keys to delete
     */
    void delete(List<K> keys);

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
     * Gets the release queue
     * 
     * @return The release queue
     */
    Queue<K> getReleaseQueue();
}
