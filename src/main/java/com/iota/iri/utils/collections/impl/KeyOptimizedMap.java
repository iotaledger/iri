package com.iota.iri.utils.collections.impl;

import com.iota.iri.utils.collections.interfaces.TransformingMap;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class KeyOptimizedMap<K,V> implements TransformingMap<K,V> {
    private Map<K,V> delegateMap;
    private UnaryOperator<K> keyOptimizer;
    private UnaryOperator<V> valueTransformer;

    public KeyOptimizedMap(UnaryOperator<K> keyOptimizer, UnaryOperator<V> valueTransformer) {
        this(16, keyOptimizer, valueTransformer);
    }

    public KeyOptimizedMap(int initialCapacity, UnaryOperator<K> keyOptimizer, UnaryOperator<V> valueTransformer) {
        this.keyOptimizer = keyOptimizer == null ? UnaryOperator.identity() : keyOptimizer;
        this.valueTransformer = valueTransformer == null ? UnaryOperator.identity() : valueTransformer;

        this.delegateMap = new HashMap<>(initialCapacity);
    }

    @Override
    public int size() {
        return delegateMap.size();
    }

    @Override
    public boolean isEmpty() {
        return delegateMap.isEmpty();
    }

    @Override
    public boolean containsKey(K key) {
        K newKey = keyOptimizer.apply(key);
        return delegateMap.containsKey(newKey);
    }

    @Override
    public boolean containsValue(K value) {
        return false;
    }



    @Override
    public V get(K key) {
        K newKey = keyOptimizer.apply(key);
        return delegateMap.get(newKey);
    }

    @Override
    public V put(K key, V value) {
        key = keyOptimizer.apply(key);
        value = valueTransformer.apply(value);
        return delegateMap.put(key, value);
    }

    @Override
    public V remove(K key) {
        return delegateMap.remove(key);
    }

    @Override
    public void clear() {
        delegateMap.clear();
    }

    @Override
    public Collection<V> values() {
        return delegateMap.values();
    }
}
