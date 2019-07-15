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
     * @see Map#size()
     * @return {@link Map#size()}
     */
    int size();

    /**
     * @see Map#isEmpty()
     * @return {@link Map#isEmpty()}
     */
    boolean isEmpty();

    /**
     * @see Map#containsKey(Object)
     * @param key {@link Map#containsKey(Object)}
     * @return {@link Map#containsKey(Object)}
     */
    boolean containsKey(K key);

    /**
     * @see Map#containsValue(Object)
     * @param value {@link Map#containsValue(Object)}
     * @return {@link Map#containsValue(Object)}
     */
    boolean containsValue(V value);

    /**
     * @see Map#get(Object)
     * @param key {@link Map#get(Object)}
     * @return {@link Map#get(Object)}
     */
    V get(K key);

    /**
     * @see Map#put(Object, Object)
     * @param key {@link Map#put(Object, Object)}
     * @param value {@link Map#put(Object, Object)}
     * @return {@link Map#put(Object, Object)}
     */
    V put(K key, V value);

    /**
     * @see Map#remove(Object)
     * @param key {@link Map#remove(Object)}
     * @return {@link Map#remove(Object)}
     */
    V remove(K key);

    /**
     * @see Map#clear()
     */
    void clear();

    /**
     * @see Map#values()
     * @return {@link Map#values()}
     */
    Collection<V> values();
}
