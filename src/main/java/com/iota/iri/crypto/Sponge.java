package com.iota.iri.crypto;

public interface Sponge {
    int HASH_LENGTH = 243;

    void absorb(final byte[] trits, int offset, int length);
    void squeeze(final byte[] trits, int offset, int length);
    void reset();
}
