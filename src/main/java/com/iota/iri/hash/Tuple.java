package com.iota.iri.hash;

/**
 * Created by paul on 2/18/17.
 */
public class Tuple {
    public static final int RADIX = 3;
    public static final int MAX_TRIT_VALUE = (RADIX - 1) / 2, MIN_TRIT_VALUE = -MAX_TRIT_VALUE;
    private static final int HIGH_BITS = 0b11111111111111111111111111111111;
    private static final int LOW_BITS = 0b00000000000000000000000000000000;
    public int low = HIGH_BITS;
    public int hi = LOW_BITS;

    public Tuple() {
        low = HIGH_BITS;
        hi = HIGH_BITS;
    }
    public Tuple(int trit) {
        low = trit == 0 ? HIGH_BITS: (trit == 1 ? LOW_BITS: HIGH_BITS);
        hi = trit == 0 ? HIGH_BITS: (trit == 1 ? HIGH_BITS: LOW_BITS);
    }
    public int value() {
        return low == LOW_BITS ? MAX_TRIT_VALUE : hi == LOW_BITS ? MIN_TRIT_VALUE : 0;
    }
}
