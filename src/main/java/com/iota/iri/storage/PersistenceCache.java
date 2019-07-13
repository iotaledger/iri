package com.iota.iri.storage;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.utils.Pair;

public class PersistenceCache<T extends Persistable> implements DataCache<Indexable, T> {

    private static final Logger log = LoggerFactory.getLogger(PersistenceCache.class);

    private Object lock = new Object();

    private PersistenceProvider persistance;

    private ListOrderedMap<Indexable, T> cache;

    private final int calculatedMaxSize;

    private Class<?> model;

    int counter = 0;

    public PersistenceCache(PersistenceProvider persistance, int cacheSizeInBytes, Class<?> persistableModel) {
        this.persistance = persistance;
        this.model = persistableModel;

        cache = new ListOrderedMap<Indexable, T>();

        // CacheSize divided by trytes to bytes conversion of size per transaction
        calculatedMaxSize = (int) Math
                .ceil(cacheSizeInBytes / (TransactionViewModel.SIZE * 3 * Math.log(3) / Math.log(2) / 8));
    }

    @Override
    public void shutdown() {
        try {
            writeAll();
        } catch (CacheException e) {
            // We cannot handle this during shutdown
            log.warn(e.getMessage());
            e.printStackTrace();
        }
        cache.clear();
    }

    @Override
    public void writeAll() throws CacheException {
        synchronized (lock) {
            try {
                writeBatch(cache.entrySet().stream().map(entry -> {
                    try {

                        return new Pair<Indexable, Persistable>(entry.getKey(), entry.getValue());
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                        e.printStackTrace();
                    }
                    return null;
                }).collect(Collectors.toList()));
            } catch (Exception e) {
                e.printStackTrace();
                throw new CacheException(e.getMessage());
            }
        }
    }

    @Override
    public T get(Indexable key) throws CacheException {
        T tvm = cache.get(key);
        if (null != tvm) {
            counter--;
            return tvm;
        }

        try {
            tvm = (T) persistance.get(model, key);
            counter++;
        } catch (Exception e) {
            throw new CacheException(e.getMessage());
        }

        add(key, tvm);
        return tvm;
    }

    @Override
    public boolean contains(Indexable key) {
        return cache.containsKey(key);
    }

    @Override
    public void add(Indexable key, T value) throws CacheException {
        if (isFullAfterAdd()) {
            cleanUp();
        }

        cache.put(key, value);
    }

    private void cleanUp() throws CacheException {
        synchronized (lock) {
            log.debug("Cleaning cache...");
            try {
                // Write in batch to the database
                writeBatch(cache.entrySet().stream().limit(getNumEvictions()).map(entry -> {
                    return new Pair<Indexable, Persistable>(entry.getKey(), entry.getValue());
                }).collect(Collectors.toList()));

                // Then remove one by one
                for (int i = 0; i < getNumEvictions(); i++) {
                    cache.remove(cache.firstKey());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void writeBatch(List<Pair<Indexable, Persistable>> models) throws Exception {
        persistance.saveBatch(models);
    }

    private boolean isFullAfterAdd() {
        return cache.size() + 1 >= calculatedMaxSize;
    }

    private int getNumEvictions() {
        return (int) Math.ceil(calculatedMaxSize / 20.0); // Clean up 5%
    }

    @Override
    public int getMaxSize() {
        return calculatedMaxSize;
    }
}
