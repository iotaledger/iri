package com.iota.iri.cache.impl;

import com.iota.iri.cache.Cache;
import com.iota.iri.cache.CacheConfiguration;
import com.iota.iri.cache.CacheManager;
import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.conf.DbConfig;
import com.iota.iri.controllers.ApproveeViewModel;
import com.iota.iri.controllers.MilestoneViewModel;
import com.iota.iri.controllers.TransactionViewModel;
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
    private DbConfig dbConfig;

    /**
     * Constructor
     */
    public CacheManagerImpl(DbConfig dbConfig) {
        cacheMap = new ConcurrentHashMap<>();
        this.dbConfig = dbConfig;
        initializeCaches();
    }

    private void initializeCaches() {
        add(TransactionViewModel.class,
                new CacheConfigurationImpl(dbConfig.getTxBatchWrite(), dbConfig.getTxBatchEvictionCount()));
        add(ApproveeViewModel.class,
                new CacheConfigurationImpl(dbConfig.getTxBatchWrite(), dbConfig.getTxBatchEvictionCount()));
        add(MilestoneViewModel.class, new CacheConfigurationImpl(dbConfig.getMilestoneBatchWrite(),
                dbConfig.getMilestoneBatchEvictionCount()));
    }

    @Override
    public <V> Cache<Indexable, V> getCache(Class<V> type) {
        Cache<Indexable, V> cache = cacheMap.get(type);
        if (cache == null) {
            return add(type, new CacheConfigurationImpl(BaseIotaConfig.Defaults.TX_BATCH_WRITE,
                    BaseIotaConfig.Defaults.TX_BATCH_EVICTION_COUNT));
        }
        return cache;
    }

    @Override
    public <V> Cache<Indexable, V> lookup(Class<V> type) {
        return cacheMap.get(type);
    }

    @Override
    public <V> Cache<Indexable, V> add(Class<V> type, CacheConfiguration cacheConfiguration) {
        Cache<Indexable, V> cache = new CacheImpl<>(cacheConfiguration);
        cacheMap.putIfAbsent(type, cache);
        return cache;
    }

    @Override
    public void clearAllCaches() {
        cacheMap.entrySet().stream().forEach(entry -> entry.getValue().clear());
        cacheMap.clear();
    }
}
