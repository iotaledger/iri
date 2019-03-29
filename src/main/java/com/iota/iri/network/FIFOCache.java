package com.iota.iri.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FIFOCache<K, V> {
    private static final Logger log = LoggerFactory.getLogger(FIFOCache.class);

    private ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private final int capacity;
    private final double dropRate;
    private LinkedHashMap<K, V> map;
    private final SecureRandom rnd = new SecureRandom();

    public FIFOCache(int capacity, double dropRate) {
        this.capacity = capacity;
        this.dropRate = dropRate;
        this.map = new LinkedHashMap<>();
    }

    public V get(K key) {
        cacheLock.readLock().lock();
        V value = this.map.get(key);
        if (value != null && (rnd.nextDouble() < this.dropRate)) {
            this.map.remove(key);
            cacheLock.readLock().unlock();
            return null;
        }
        cacheLock.readLock().unlock();
        return value;
    }

    public V put(K key, V value) {
        cacheLock.writeLock().lock();
        if (this.map.containsKey(key)) {
            cacheLock.writeLock().unlock();
            return value;
        }
        if (this.map.size() >= this.capacity) {
            Iterator<K> it = this.map.keySet().iterator();
            it.next();
            it.remove();
        }
        cacheLock.writeLock().unlock();
        return this.map.put(key, value);
    }
}
