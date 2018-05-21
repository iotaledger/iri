package com.iota.iri.utils.collections.impl;

import com.iota.iri.utils.collections.interfaces.OptimizedMap;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class KeyOptimizedMap<K,V,T> implements OptimizedMap<K,V> {
    private Map<T, V> delegateMap;
    private Function<K, T> optimizationFunction;

    public KeyOptimizedMap(Function<K, T> optimizationFunction) {
        this(16, optimizationFunction);
    }

    @SuppressWarnings("unchecked")
    public KeyOptimizedMap(int initialCapacity, Function<K, T> optimizationFunction) {
        this.optimizationFunction = optimizationFunction == null
                ? (Function<K, T>) Function.identity()
                : optimizationFunction;

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
        T newKey = optimizationFunction.apply(key);
        return delegateMap.containsKey(newKey);
    }

    @Override
    public boolean containsValue(K value) {
        return false;
    }


    @Override
    public V get(K key) {
        T newKey = optimizationFunction.apply(key);
        return delegateMap.get(newKey);
    }

    @Override
    public V put(K key, V value) {
        T newKey = optimizationFunction.apply(key);
        return delegateMap.put(newKey, value);
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
