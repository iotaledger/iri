package com.iota.iri.network;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The {@link FIFOCache} is a simple FIFO cache which removes entries at the front of the queue when the capacity is
 * reached.
 * 
 * @param <K> the key type
 * @param <V> the value type
 */
public class FIFOCache<K, V> {

    private ReadWriteLock cacheLock = new ReentrantReadWriteLock(true);
    private final int capacity;
    private Map<K, V> map = new LinkedHashMap<>();
    private AtomicLong cacheHits = new AtomicLong();
    private AtomicLong cacheMisses = new AtomicLong();

    /**
     * Creates a new {@link FIFOCache}.
     * 
     * @param capacity the maximum capacity of the cache
     */
    public FIFOCache(int capacity) {
        this.capacity = capacity;
    }

    /**
     * Gets the entry by the given key.
     * 
     * @param key the key to use to retrieve the entry
     * @return the entry
     */
    public V get(K key) {
        try {
            cacheLock.readLock().lock();
            V v = this.map.get(key);
            if (v == null) {
                cacheMisses.incrementAndGet();
            } else {
                cacheHits.incrementAndGet();
            }
            return v;
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Adds the given entry by the given key.
     * 
     * @param key   the key to use for the entry
     * @param value the value of the entry
     * @return the added entry
     */
    public V put(K key, V value) {
        try {
            cacheLock.writeLock().lock();
            if (this.map.containsKey(key)) {
                return value;
            }
            if (this.map.size() >= this.capacity) {
                Iterator<K> it = this.map.keySet().iterator();
                it.next();
                it.remove();
            }
            return this.map.put(key, value);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Gets the amount of cache hits.
     * 
     * @return amount of cache hits
     */
    public long getCacheHits() {
        return cacheHits.get();
    }

    /**
     * Gets the amount of ache misses.
     * 
     * @return amount of cache misses
     */
    public long getCacheMisses() {
        return cacheMisses.get();
    }

    /**
     * Resets the cache hits and misses stats back to 0.
     */
    public void resetCacheStats() {
        cacheHits.set(0);
        cacheMisses.set(0);
    }
}
