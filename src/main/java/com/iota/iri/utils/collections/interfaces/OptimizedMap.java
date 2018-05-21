package com.iota.iri.utils.collections.interfaces;

import com.iota.iri.storage.Indexable;

import java.util.Collection;
import java.util.Map;


/**
 * Similar to {@link Map} but hides key retrieval functionality.
 * This is because optimization on the keys doesn't allow us to get the original key back
 *
 * @param <K> The key type
 * @param <V> The value type
 */
public interface OptimizedMap<K,V> {


    /**
     * {See {@link Map#size()}}
     */
    int size();

    /**
     * {See {@link Map#isEmpty()}}
     */
    boolean isEmpty();

    /**
     * {See {@link Map#containsKey(Object)}}
     */
    boolean containsKey(K key);

    /**
     * {See {@link Map#containsValue(Object)}}
     */
    boolean containsValue(K value);

    /**
     *
     * {See {@link Map#get}}
     */
    V get(K key);

    /**
     * {See {@link Map#put}
     */
    V put(K key, V value);

    /**
     * {See {@link Map#keySet()}}
     */
    V remove(K key);

    /**
     * {See {@link Map#clear()}}
     */
    void clear();

    /**
     * {See {@link Map#values}
     */
    Collection<V> values();
}
