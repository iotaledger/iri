package com.iota.iri.crypto;

/**
 * Hash functions from the Sponge family.
 * @see <a href="https://en.wikipedia.org/wiki/Sponge_function">https://en.wikipedia.org/wiki/Sponge_function</a><br>
 *
 * Popular usage: Hash(x)
 * <pre>
 * new sponge <br>
 * sponge.absorb(x) <br>
 * return sponge.squeeze() <br>
 * </pre>
 *
 */
public interface Sponge {
    int HASH_LENGTH = 243;

    /**
     * Absorbs {@code trits}, in chunks of {@value #HASH_LENGTH}.<br>
     * can be called consecutively to absorb more trits.
     *
     * @param trits trits array to be absorbed by the sponge
     * @param offset starting position in trits array
     * @param length amount of trits to absorb, multiple of {@value #HASH_LENGTH}
     */
    void absorb(final byte[] trits, int offset, int length);

    /**
     * Squeezes {@code length} trits from the sponge, in chunks of {@value #HASH_LENGTH}.<br>
     * can be called consecutively to squeeze more trits.<br>
     * this method will override the content of {@code trits}
     *
     * @param trits trits array to write squeezed trits to
     * @param offset starting position to write to in trits array
     * @param length amount of trits to squeeze, multiple of {@value #HASH_LENGTH}
     */
    void squeeze(final byte[] trits, int offset, int length);

    /**
     * Resets the internal state of the sponge.<br>
     * Can be used to re-use a Sponge object.
     */
    void reset();
}
