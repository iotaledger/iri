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
    //weak store. Eligible for GC
    private final ConcurrentMap<K,V> weakStore;
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
        this.strongStore = new MapMaker().concurrencyLevel(5).makeMap();
        this.weakStore = new MapMaker().concurrencyLevel(5).weakValues().makeMap();
        this.evictionQueue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public V get(K key) {
        if(key == null){
            return null;
        }
        V value = strongStore.get(key);
        if(value == null && weakStore.containsKey(key)){
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
    public V lookup(K key) {
        return get(key);
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
        if (getSize() == cacheConfiguration.getMaxSize()) {
            evict();
        }
        // new entry
        if (strongStore.put(key, value) == null) {
            evictionQueue.offer(key);
        }
    }

    @Override
    public void putIfAbsent(K key, V value) {
        if (getSize() == cacheConfiguration.getMaxSize()) {
            evict();
        }
        if (!strongStore.containsKey(key)) {
            put(key, value);
            evictionQueue.offer(key);
        }
    }

    @Override
    public void evict(K key) {
        if(key == null || !strongStore.containsKey(key)){
            return;
        }
        V value = strongStore.get(key);
        weakStore.put(key, value);
        strongStore.remove(key);
        evictionQueue.remove(key);
    }

    @Override
    public void evict() {
        for (int i = 0; i < cacheConfiguration.getEvictionCount(); i++) {
           evict(evictionQueue.peek());
        }
    }

    @Override
    public void evict(List<K> keys) {
        keys.forEach(key -> {
            evict(key);
        });
    }

    @Override
    public void clear() {
        strongStore.clear();
        weakStore.clear();
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
}
