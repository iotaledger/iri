package com.iota.iri.storage;

/**
 * 
 * A data cache wraps around a storage object. It will keep information in its
 * memory for a while, preventing duplicate read/writes.
 *
 */
public interface DataCache<K, V> {

    void shutdown();

    /**
     * 
     * @throws CacheException
     */
    void writeAll() throws CacheException;

    /**
     * 
     * @param key
     * @return
     * @throws CacheException
     */
    V get(K key) throws CacheException;

    /**
     * 
     * @param key
     * @return
     */
    boolean contains(K key);

    void add(Indexable key, V value) throws CacheException;

    int getMaxSize();
}
