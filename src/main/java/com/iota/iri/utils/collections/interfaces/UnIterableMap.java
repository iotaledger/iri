package com.iota.iri.utils.collections.interfaces;

import java.util.Collection;
import java.util.Map;


/**
 * Similar to {@link Map} but hides key retrieval functionality.
 * Thus one can't iterate over key or entries.
 * Implementing class may transform keys to perform memory operations
 *
 * @param <K> The key type
 * @param <V> The value type
 */
public interface UnIterableMap<K,V> {


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
    boolean containsValue(V value);

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
