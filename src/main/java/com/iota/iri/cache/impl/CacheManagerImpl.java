package com.iota.iri.cache.impl;

import com.iota.iri.cache.Cache;
import com.iota.iri.cache.CacheManager;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class CacheManagerImpl implements CacheManager {

    ConcurrentHashMap<String, Cache> cacheMap;

    @Override
    public Collection<String> getCacheNames() {
        return null;
    }

    @Override
    public Cache getCache(String name) {
        return null;
    }

    @Override
    public Cache lookup(String name) {
        return null;
    }

    @Override
    public void add(Cache cache) {

    }
}
