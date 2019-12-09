package com.iota.iri.cache.impl;

import com.iota.iri.cache.Cache;
import com.iota.iri.cache.CacheManager;
import com.iota.iri.storage.Indexable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache Manager
 */
public class CacheManagerImpl implements CacheManager {

    /**
     * Cache map to store caches
     */
    ConcurrentHashMap<Class<?>, Cache> cacheMap;

    /**
     * Constructor
     */
    public CacheManagerImpl() {
        cacheMap = new ConcurrentHashMap<>();
    }

    @Override
    public <V> Cache<Indexable, V> getCache(Class<V> type) {
        Cache<Indexable, V> cache = cacheMap.get(type);
        if (cache == null) {
            cache = add(type);
        }
        return cache;
    }

    @Override
    public <V> Cache<Indexable, V> lookup(Class<V> type) {
        return cacheMap.get(type);
    }

    @Override
    public <V> Cache add(Class<V> type) {
        Cache<Indexable, V> cache = new CacheImpl<>(new CacheConfigurationImpl());
        cacheMap.put(type, cache);
        return cache;
    }

    @Override
    public void clearAllCaches() {
        cacheMap.entrySet().stream().forEach(entry -> entry.getValue().clear());
        cacheMap.clear();
    }
}
