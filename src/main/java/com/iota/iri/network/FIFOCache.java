package com.iota.iri.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The {@link FIFOCache} is a simple FIFO cache which removes entries at the front of the queue when the capacity is
 * reached. The {@link FIFOCache} might also randomly drop entries defined as per the given drop rate.
 * 
 * @param <K> the key type
 * @param <V> the value type
 */
public class FIFOCache<K, V> {

    private static final Logger log = LoggerFactory.getLogger(FIFOCache.class);

    private ReadWriteLock cacheLock = new ReentrantReadWriteLock(true);
    private final int capacity;
    private final double dropRate;
    private LinkedHashMap<K, V> map;
    private final SecureRandom rnd = new SecureRandom();

    /**
     * Creates a new {@link FIFOCache}.
     * 
     * @param capacity the maximum capacity of the cache
     * @param dropRate the rate at which to randomly drop entries
     */
    public FIFOCache(int capacity, double dropRate) {
        this.capacity = capacity;
        this.dropRate = dropRate;
        this.map = new LinkedHashMap<>();
    }

    /**
     * Gets the entry by the given key.
     * 
     * @param key the key to use to retrieve the entry
     * @return the entry
     */
    public V get(K key) {
        try{
            cacheLock.readLock().lock();
            return this.map.get(key);
        }finally{
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
        try{
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
        }finally{
            cacheLock.writeLock().unlock();
        }
    }
}
