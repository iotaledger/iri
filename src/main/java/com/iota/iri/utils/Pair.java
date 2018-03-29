package com.iota.iri.utils;

/**
 * Created by paul on 4/15/17.
 * Edited by footloosejava 03/28/2018
 */
public class Pair<S, T> {
    public final S low;
    public final T hi;

    public Pair(S k, T v) {
        low = k;
        hi = v;
    }
}
