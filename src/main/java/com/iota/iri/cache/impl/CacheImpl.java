package com.iota.iri.cache.impl;

import com.iota.iri.cache.Cache;
import com.iota.iri.cache.CacheConfiguration;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import com.google.common.collect.MapMaker;

/**
 * Cache operations
 */
public class CacheImpl<K, V> implements Cache<K, V> {

    // Config
    private final CacheConfiguration cacheConfiguration;

    // Actual store
    private final ConcurrentMap<K, V> store;

    // eviction queue
    private final ConcurrentLinkedQueue<K> evictionQueue;

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
        MapMaker mapMaker = new MapMaker().concurrencyLevel(5);
        if (cacheConfiguration.isWeakReference()) {
            mapMaker = mapMaker.weakValues();
        }
        this.store = mapMaker.makeMap();
        this.evictionQueue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public V get(K key) {
        V value = store.get(key);
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
    public List<K> getKeys() {
        return store.keySet().stream().collect(Collectors.toList());
    }

    @Override
    public int getSize() {
        return store.size();
    }

    @Override
    public void put(K key, V value) {
        if (getSize() == cacheConfiguration.getMaxSize()) {
            evict();
        }
        // new entry
        if (store.put(key, value) == null) {
            evictionQueue.offer(key);
        }
    }

    @Override
    public void putIfAbsent(K key, V value) {
        if (getSize() == cacheConfiguration.getMaxSize()) {
            evict();
        }
        if (!store.containsKey(key)) {
            put(key, value);
            evictionQueue.offer(key);
        }
    }

    @Override
    public void evict(K key) {
        if (store.remove(key) != null) {
            evictionQueue.remove(key);
        }
    }

    @Override
    public void evict() {
        for (int i = 0; i < cacheConfiguration.getEvictionCount(); i++) {
            K key = evictionQueue.peek();
            if (store.remove(key) != null) {
                evictionQueue.poll();
            }
        }
    }

    @Override
    public void evict(List<K> keys) {
        keys.forEach(key -> {
            if (store.remove(key) != null) {
                evictionQueue.remove(key);
            }
        });
    }

    @Override
    public void clear() {
        store.clear();
        evictionQueue.clear();
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
    public K nextEvictionKey() {
        return evictionQueue.peek();
    }

    @Override
    public void cleanEvictionQueue() {
        if (evictionQueue.size() <= getSize()) {
            return;
        }

        evictionQueue.forEach(key -> {
            if (!store.containsKey(key)) {
                evictionQueue.remove(key);
            }
        });
    }
}
