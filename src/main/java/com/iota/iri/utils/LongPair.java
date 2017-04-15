package com.iota.iri.utils;

/**
 * Created by paul on 4/15/17.
 */
public class LongPair extends Pair<Long, Long> {
    private static final long HIGH_BITS = 0xFFFFFFFFFFFFFFFFL;
    private static final long LOW_BITS = 0L;
    public static LongPair fromTrit(int trit) {
        return new LongPair(trit != 1 ? HIGH_BITS : LOW_BITS, trit != -1 ? HIGH_BITS : LOW_BITS);
    }
    public int toTrit() {
        return key() == LOW_BITS ? 1 : (value() == LOW_BITS) ? -1 : 0;
    }
    public LongPair(Long k, Long v) {
        super(k, v);
    }
}
