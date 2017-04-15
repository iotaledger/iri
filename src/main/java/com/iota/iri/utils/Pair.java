package com.iota.iri.utils;

/**
 * Created by paul on 4/15/17.
 */
public abstract class Pair<S, T> {
    private final S key;
    private final T value;
    public Pair(S k, T v) {
        key = k;
        value = v;
    }
    public S key() {
        return key;
    }
    public T value() {
        return value;
    }
}
