package com.iota.iri.cache.impl;

import com.iota.iri.cache.Cache;
import com.iota.iri.cache.CacheConfiguration;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import com.google.common.collect.MapMaker;

/**
 * Implementation of {@link Cache} interface. The cache is mapping from keys to values.
 * 
 * Cache entries are added by calling {@link Cache#put(Object, Object)} which adds the mapping to a strong store until
 * released either manually or when the cache is full according to its {@link CacheConfiguration}. An entry is released
 * by removing it from the strong store and putting in the weak store.
 * 
 * This cache uses a FIFO strategy with the help of a release queue.
 * 
 * This cache does not store null keys or null values.
 * 
 * A value is gotten by calling {@link Cache#get(Object)} with the specified key.
 * 
 * The stores ues a {@link java.util.concurrent.ConcurrentMap} which are thread safe.
 * 
 */
public class CacheImpl<K, V> implements Cache<K, V> {

    /**
     * Configuration used to initialize the stores.
     */

    private final CacheConfiguration cacheConfiguration;

    /**
     * The map to store key-value pairs.
     */
    private final ConcurrentMap<K, V> strongStore;

    /**
     * Values in this map are weak and are eligible for garbage collection. If a value is still in this store during a
     * cache get, it'll be brought back to the strong store.
     */
    private final ConcurrentMap<K, V> weakStore;

    /**
     * Cache entries will be released in the order in which they are enqueued.
     */
    private final ConcurrentLinkedQueue<K> releaseQueue;

    // stats
    private int cacheHits = 0;
    private int cacheMisses = 0;

    /**
     * Constructor
     * 
     * @param cacheConfiguration The configuration to use to instantiate this cache
     */
    public CacheImpl(CacheConfiguration cacheConfiguration) {
        this.cacheConfiguration = cacheConfiguration;
        this.strongStore = new MapMaker().concurrencyLevel(cacheConfiguration.getConcurrencyLevel()).makeMap();
        this.weakStore = new MapMaker().concurrencyLevel(cacheConfiguration.getConcurrencyLevel()).weakValues()
                .makeMap();
        this.releaseQueue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public V get(K key) {
        if (key == null) {
            return null;
        }
        V value = strongStore.get(key);

        if (value == null) {
            value = weakStore.get(key);
            if (value != null) {
                put(key, value);
                weakStore.remove(key);
            }
        }
        if (value != null) {
            cacheHit();
        } else {
            cacheMiss();
        }
        return value;
    }

    @Override
    public Map<K, V> getAll(Collection<K> keys) {
        Map<K, V> result = new HashMap<>();
        keys.stream().forEach(key -> result.put(key, get(key)));
        return result;
    }

    @Override
    public int getSize() {
        return strongStore.size();
    }

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key, "Cache key cannot be null");
        Objects.requireNonNull(value, "Cache value cannot be null");

        // new entry
        if (strongStore.put(key, value) == null) {
            releaseQueue.offer(key);
        }
    }

    @Override
    public void release(K key) {
        if (key == null) {
            return;
        }
        V value = strongStore.remove(key);
        releaseQueue.remove(key);
        if (value != null) {
            weakStore.put(key, value);
        }
    }

    @Override
    public void release() {
        for (int i = 0; i < cacheConfiguration.getReleaseCount(); i++) {
            release(releaseQueue.peek());
        }
    }

    @Override
    public void release(List<K> keys) {
        keys.forEach(this::release);
    }

    @Override
    public void delete(K key) {
        if (key == null) {
            return;
        }
        strongStore.remove(key);
        weakStore.remove(key);
        releaseQueue.remove(key);
    }

    @Override
    public void delete(List<K> keys) {
        keys.forEach(key -> delete(key));
    }

    @Override
    public void clear() {
        strongStore.clear();
        weakStore.clear();
        releaseQueue.clear();
    }

    private void cacheHit() {
        cacheHits++;
    }

    private void cacheMiss() {
        cacheMisses++;
    }

    @Override
    public int getCacheHits() {
        return cacheHits;
    }

    @Override
    public int getCacheMisses() {
        return cacheMisses;
    }

    @Override
    public CacheConfiguration getConfiguration() {
        return cacheConfiguration;
    }

    @Override
    public ConcurrentLinkedQueue<K> getReleaseQueue() {
        return releaseQueue;
    }
}
