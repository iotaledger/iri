package com.iota.iri.storage;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iota.iri.controllers.TransactionViewModel;
import com.iota.iri.utils.Pair;

/**
 * <p>
 * Persistence cache is a caching layer that goes over a
 * {@link PersistenceProvider}. When we request a value from the cache, we check
 * if it is already cached, and if it isn't, we ask the
 * {@link PersistenceProvider}.
 * </p>
 * <p>
 * When a value gets requested, which is already added, this value will not move
 * its position back in the front of the cache. Once the cache gets filled until
 * {@link #getMaxSize()}, we clean the oldest 5%.
 * </p>
 *
 * @param <T>
 */
public class PersistenceCache<T extends Persistable> implements DataCache<Indexable, T> {

    private static final Logger log = LoggerFactory.getLogger(PersistenceCache.class);

    /**
     * The percentage of the cache we clear when we are full. Must be at least 1%.
     */
    private static final int PERCENT_CLEAN = 5;

    private Object lock = new Object();

    /**
     * The persistence we use to request values which are not yet cached.
     */
    private PersistenceProvider persistance;

    /**
     * ListOrdered cache, chosen because it has no extras, doesn't modify position
     * on read and on double-add.
     */
    private ListOrderedMap<Indexable, T> cache;

    /**
     * Maximum size of the cache, based on the amount of transaction data we can fit
     * in the bytes passed in the constructor.
     */
    private final int calculatedMaxSize;

    /**
     * The model of the generic type of this class
     */
    private Class<? extends Persistable> model;

    /**
     * Creates a new instance of the cache.
     * 
     * @param persistance      The persistence we use to request values which are
     *                         not yet cached.
     * @param cacheSizeInBytes The size of the cache we want to maintain in memory,
     *                         in bytes.
     * @param persistableModel The model this cache persists.
     */
    public PersistenceCache(PersistenceProvider persistance, int cacheSizeInBytes,
            Class<? extends Persistable> persistableModel) {

        this.persistance = persistance;
        this.model = persistableModel;

        cache = new ListOrderedMap<Indexable, T>();

        // CacheSize divided by trytes to bytes conversion of size per transaction
        calculatedMaxSize = (int) Math
                .ceil(cacheSizeInBytes / (TransactionViewModel.SIZE * 3 * Math.log(3) / Math.log(2) / 8));
    }

    @Override
    public void shutdown() {
        synchronized (lock) {
            try {
                writeAll();
            } catch (CacheException e) {
                // We cannot handle this during shutdown
                log.warn(e.getMessage());
                e.printStackTrace();
            }
            cache.clear();
        }
    }

    /**
     * 
     * {@inheritDoc}
     * 
     * Writing to the database happens in a single batch.
     */
    @Override
    public void writeAll() throws CacheException {
        synchronized (lock) {
            try {
                writeBatch(cache.entrySet().stream().map(entry -> {
                    try {

                        return new Pair<Indexable, Persistable>(entry.getKey(), entry.getValue());
                    } catch (Exception e) {
                        log.error(e.getMessage());
                        return null;
                    }
                }).collect(Collectors.toList()));
            } catch (Exception e) {
                throw new CacheException(e.getMessage());
            }
        }
    }

    @Override
    public T get(Indexable key) throws CacheException {
        T tvm = cache.get(key);
        if (null != tvm) {
            return tvm;
        }

        try {
            tvm = (T) persistance.get(model, key);
        } catch (Exception e) {
            throw new CacheException(e.getMessage());
        }

        if (null != tvm) {
            add(key, tvm);
        }
        return tvm;
    }

    @Override
    public boolean contains(Indexable key) {
        return cache.containsKey(key);
    }

    @Override
    public void add(Indexable key, T value) throws CacheException {
        synchronized (lock) {
            if (isFullAfterAdd()) {
                cleanUp();
            }

            cache.put(key, value);
        }
    }

    private void cleanUp() throws CacheException {
        log.debug("Cleaning cache...");
        try {
            List<Pair<Indexable, Persistable>> listBatch = null;
            try {
                listBatch = cache.entrySet().stream().limit(getNumEvictions()).map(entry -> {
                    System.out.println("cleaning batch entry: " + entry);
                    return new Pair<Indexable, Persistable>(entry.getKey(), entry.getValue());
                }).collect(Collectors.toList());
            } catch (Exception e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }

            // Write in batch to the database
            writeBatch(listBatch);

            // Then remove one by one
            for (int i = 0; i < getNumEvictions(); i++) {
                System.out.println("Cache remove " + i);
                cache.remove(cache.firstKey());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeBatch(List<Pair<Indexable, Persistable>> models) throws Exception {
        persistance.saveBatch(models);
    }

    private boolean isFullAfterAdd() {
        return cache.size() + 1 >= calculatedMaxSize;
    }

    private int getNumEvictions() {
        return (int) Math.ceil(calculatedMaxSize / 100.0 * PERCENT_CLEAN); // Clean up 5%
    }

    @Override
    public int getMaxSize() {
        return calculatedMaxSize;
    }
}
