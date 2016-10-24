package iri;

import cfb.curl.*;

import java.util.*;

public class Hash {

    public static final int SIZE_IN_BYTES = 49;

    public static final Hash NULL_HASH = new Hash(new int[Curl.HASH_LENGTH]);

    public final byte[] bytes;

    private final int hashCode;

    public Hash(final byte[] bytes, final int offset, final int size) {

        this.bytes = new byte[SIZE_IN_BYTES];
        System.arraycopy(bytes, offset, this.bytes, 0, size);

        hashCode = Arrays.hashCode(this.bytes);
    }

    public Hash(final byte[] bytes) {

        this(bytes, 0, SIZE_IN_BYTES);
    }

    public Hash(final int[] trits, final int offset) {

        this(Converter.bytes(trits, offset, Curl.HASH_LENGTH));
    }

    public Hash(final int[] trits) {

        this(trits, 0);
    }

    public Hash(final String trytes) {

        this(Converter.trits(trytes));
    }

    public int[] trits() {

        final int[] trits = new int[Curl.HASH_LENGTH];
        Converter.getTrits(bytes, trits);

        return trits;
    }

    @Override
    public boolean equals(final Object obj) {

        return Arrays.equals(bytes, ((Hash)obj).bytes);
    }

    @Override
    public int hashCode() {

        return hashCode;
    }

    @Override
    public String toString() {

        return Converter.trytes(trits());
    }
}
