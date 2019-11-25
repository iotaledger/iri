package com.iota.iri.cache.impl;

import com.iota.iri.cache.Cache;
import com.iota.iri.cache.CacheConfiguration;
import com.iota.iri.cache.util.ValueWrapper;
import com.iota.iri.storage.PersistenceProvider;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import com.google.common.collect.MapMaker;

public class CacheImpl implements Cache {

    private final String name;
    private final CacheConfiguration cacheConfiguration;
    private final ConcurrentMap<Object, Object> store;

    public CacheImpl(String name) {
        this(name, new CacheConfiguration());
    }

    public CacheImpl(String name, CacheConfiguration cacheConfiguration) {
        this.name = name;
        this.cacheConfiguration = cacheConfiguration;
        MapMaker mapMaker = new MapMaker().concurrencyLevel(5);
        if (cacheConfiguration.isWeakReference()) {
            mapMaker = mapMaker.weakKeys();
        }
        this.store = mapMaker.makeMap();
    }

    @Override
    public void clear() {

    }

    @Override
    public void evict(Object key) {

    }

    @Override
    public void evict() {

    }

    @Override
    public void evict(Collection<Object> keys) {

    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public ValueWrapper get(Object key) {
        return null;
    }

    @Override
    public <T> T get(Object key, Class<T> type) throws IllegalStateException {
        return null;
    }

    @Override
    public <T> T get(Object key, PersistenceProvider persistenceProvider) {
        return null;
    }

    @Override
    public Map<Object, ValueWrapper> getAll(Collection<Object> keys) {
        return null;
    }

    @Override
    public Collection<Object> getKeys() {
        return null;
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public void put(Object key, Object value) {

    }

    @Override
    public void putIfAbsent(Object key, Object value) {

    }

    @Override
    public void putAll(Collection<Object> values) {

    }
}
