package com.iota.iri.utils;

/**
 * Created by paul on 4/15/17.
 */
public class IntPair extends Pair<Integer, Integer> {
    private static final int HIGH_BITS = 0xFFFFFFFF;
    private static final int LOW_BITS = 0;
    public IntPair(Integer k, Integer v) {
        super(k, v);
    }
    public static IntPair fromTrit(int trit) {
        return new IntPair(trit != 1 ? HIGH_BITS : LOW_BITS, trit != -1 ? HIGH_BITS : LOW_BITS);
    }
    public int toTrit() {
        return key() == LOW_BITS ? 1 : (value() == LOW_BITS) ? -1 : 0;
    }
}
