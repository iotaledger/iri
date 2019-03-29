package com.iota.iri.crypto.batched;

/**
 * Represents a single row of multiplexed binary-encoded-ternary values.
 * Following formula applies: trit value -1 => high 0, low 1,
 * trit value 0 => high 1, low 1, trit value 1 => high 1, low 0
 */
public class BCTrit {

    public long low;
    public long high;

}
