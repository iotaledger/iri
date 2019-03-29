package com.iota.iri.crypto.batched;

/**
 * Represents multiplexed binary-encoded-ternary values.
 */
public class BCTrinary {

    public long[] low;
    public long[] high;

    /**
     * Creates a new {@link BCTrinary} with the given low/high bit long values.
     * @param low the low bit values
     * @param high the high bit values
     */
    public BCTrinary(long[] low, long[] high) {
        this.low = low;
        this.high = high;
    }

}
