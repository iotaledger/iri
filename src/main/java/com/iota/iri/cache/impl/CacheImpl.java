package com.iota.iri.cache.impl;

import com.iota.iri.cache.Cache;
import com.iota.iri.cache.CacheConfiguration;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import com.google.common.collect.MapMaker;

/**
 * Cache operations
 */
public class CacheImpl<K, V> implements Cache<K, V> {

    // Config
    private final CacheConfiguration cacheConfiguration;

    // Actual store
    private final ConcurrentMap<K, V> strongStore;
    // weak store. Eligible for GC
    private final ConcurrentMap<K, V> weakStore;
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

        if (value == null && weakStore.containsKey(key)) {
            put(key, weakStore.get(key));
            value = strongStore.get(key);
            weakStore.remove(key);
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
        Map result = new HashMap<K, V>();
        keys.stream().forEach(key -> result.put(key, get(key)));
        return result;
    }

    @Override
    public int getSize() {
        return strongStore.size();
    }

    @Override
    public void put(K key, V value) {
        // new entry
        if (strongStore.put(key, value) == null) {
            releaseQueue.offer(key);
        }
    }

    @Override
    public void release(K key) {
        if (key == null || !strongStore.containsKey(key)) {
            return;
        }
        V value = strongStore.get(key);
        strongStore.remove(key);
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
        keys.forEach(key -> {
            release(key);
        });
    }

    @Override
    public void delete(K key) {
        if (key == null || !strongStore.containsKey(key)) {
            return;
        }
        strongStore.remove(key);
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
    public K nextReleaseKey() {
        return releaseQueue.peek();
    }
}
