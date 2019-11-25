package com.iota.iri.cache;

import com.iota.iri.cache.util.ValueWrapper;
import com.iota.iri.storage.PersistenceProvider;

import java.util.Collection;
import java.util.Map;

public interface Cache {

    /**
     * Get the name of this cache.
     * 
     * @return The name of the cache
     */
    String getName();

    /**
     * Get the value mapped to the specified key. Returns {@code null} if the mapping is not found.
     * 
     * @param key The keys whos value is to be returned
     * @return The mapped value of the specified key
     */
    ValueWrapper get(Object key);

    /**
     * Get the value mapped to the specified key cast to the requested type. This does not differentiate between a
     * cached {@code null} and no cached value at all. If the value is {@code null}, the return type is therefore
     * irrelevant. If the value does not match the requested type, an {@code IllegalStateException} is thrown.
     * 
     * @param key  The key whos value is to be returned
     * @param type The type of the value to match
     * @param <T>  The class type
     * @return The mapped value of the specified key.
     */
    <T> T get(Object key, Class<T> type) throws IllegalStateException;

    /**
     * Get the value mapped to the specified key obtaining the value from the persistence provider if not found in
     * cache. If cache return; Otherwise db read, cache and return.
     * 
     * @param key                 The key wohs value is to be returned
     * @param persistenceProvider
     * @param <T>                 The class type of the returned value
     * @return The mapped value of the specified key
     */
    <T> T get(Object key, PersistenceProvider persistenceProvider);

    /**
     * Get all elements from the cache for the keys specified.
     * 
     * @param keys The keys to get values of
     * @return A map of key and value associations.
     */
    Map<Object, ValueWrapper> getAll(Collection<Object> keys);

    /**
     * Get the all keys of the cache
     * 
     * @return A collection of keys in the cache
     */
    Collection<Object> getKeys();

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
    void put(Object key, Object value);

    /**
     * Associates the specified value with the specified key if the key does not already exist in the cache If the key
     * already exists, the existing value is returned.
     * 
     * @param key   The key to associate the specified value with
     * @param value The value to associate the specified key with
     */
    void putIfAbsent(Object key, Object value);

    /**
     * Puts the list of values in the cache
     * 
     * @param values values to be put in the cache
     */
    void putAll(Collection<Object> values);

    /**
     * Evict the specified key from the cache
     * 
     * @param key
     */
    void evict(Object key);

    /**
     * Evict expired items from the cache according to its {@link CacheConfiguration}.
     */
    void evict();

    /**
     * Evict all items specified in the given collection
     */
    void evict(Collection<Object> keys);

    /**
     * Clear the cache by removing all mappings
     */
    void clear();
}
