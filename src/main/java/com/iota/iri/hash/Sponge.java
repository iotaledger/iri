package com.iota.iri.hash;

public interface Sponge {
    void absorb(final int[] trits, int offset, int length);
    void squeeze(final int[] trits, int offset, int length);
    void reset();
}
