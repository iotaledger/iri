package com.iota.iri.hash;

public interface Sponge {
    int HASH_LENGTH = 243;

    void absorb(final int[] trits, int offset, int length);
    void squeeze(final int[] trits, int offset, int length);
    void reset();
}
