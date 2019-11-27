package com.iota.iri.cache.impl;

import com.iota.iri.cache.Cache;
import com.iota.iri.cache.CacheConfiguration;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.google.common.collect.MapMaker;

/**
 * Cache operations
 */
public class CacheImpl<K, V> implements Cache<K, V> {

    /**
     * Cache config
     */
    private final CacheConfiguration cacheConfiguration;

    /**
     * Store the state of entries. True for "Fresh" and False for "dirty"
     */
    private final ConcurrentMap<K, Boolean> entryState;

    /**
     * Actual cache store
     */
    private final ConcurrentMap<K, V> store;

    /**
     * Constructor
     * 
     * @param cacheConfiguration The configuration to use to instantiate this cache
     */
    public CacheImpl(CacheConfiguration cacheConfiguration) {
        this.cacheConfiguration = cacheConfiguration;
        this.entryState = new ConcurrentHashMap<>();
        MapMaker mapMaker = new MapMaker().concurrencyLevel(5);
        if (cacheConfiguration.isWeakReference()) {
            mapMaker = mapMaker.weakKeys();
        }
        this.store = mapMaker.makeMap();
    }

    @Override
    public Cache get(K key) {
        return null;
    }

    @Override
    public Map<K, V> getAll(Collection<K> keys) {
        return null;
    }

    @Override
    public Collection<K> getKeys() {
        return null;
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public void put(K key, V value) {
        // Empty body
    }

    @Override
    public void putIfAbsent(K key, V value) {
        // Empty body
    }

    @Override
    public void putAll(Collection<V> values) {
        // Empty body
    }

    @Override
    public void evict(K key) {
        // Empty body
    }

    @Override
    public void evict() {
        // Empty body
    }

    @Override
    public void evict(Collection<K> keys) {
        // Empty body
    }

    @Override
    public void clear() {
        // Empty body
    }
}
