package com.iota.iri.cache.impl;

import com.iota.iri.cache.Cache;
import com.iota.iri.cache.CacheManager;

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
    public <T> Cache getCache(Class<T> type) {
        return null;
    }

    @Override
    public <T> Cache lookup(Class<T> type) {
        return null;
    }

    @Override
    public <T> Cache add(Class<T> type) {
        return null;
    }
}
