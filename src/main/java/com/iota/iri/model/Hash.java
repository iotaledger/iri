package com.iota.iri.model;

import com.iota.iri.hash.Curl;
import com.iota.iri.utils.Converter;

import java.io.Serializable;
import java.util.Arrays;

public class Hash implements Comparable<Hash>, Serializable{

    public static final int SIZE_IN_TRITS = 243;
    public static final int SIZE_IN_BYTES = 49;

    public static final Hash NULL_HASH = new Hash(new int[Curl.HASH_LENGTH]);

    private final byte[] bytes;
    private final int hashCode;
    
    // constructors' bill

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

    //
    public static Hash calculate(final int[] tritsToCalculate, int offset, int length, final Curl curl) {
        int[] hashTrits = new int[SIZE_IN_TRITS];
        curl.reset();
        curl.absorb(tritsToCalculate, offset, length);
        curl.squeeze(hashTrits, 0, SIZE_IN_TRITS);
        return new Hash(hashTrits);
    }

    public int trailingZeros() {
        int index, zeros;
        final int[] trits;
        index = SIZE_IN_TRITS;
        zeros = 0;
        trits = trits();
        while(index-- > 0 && trits[index] == 0) {
            zeros++;
        }
        return zeros;
    }

    public int[] trits() {
        final int[] trits = new int[Curl.HASH_LENGTH];
        Converter.getTrits(bytes, trits);
        return trits;
    }

    @Override
    public boolean equals(final Object obj) {
        assert obj instanceof Hash;
        if (obj == null) return false;
        return Arrays.equals(bytes, ((Hash) obj).bytes);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return Converter.trytes(trits());
    }
    
    public byte[] bytes() {
		return bytes.clone();
	}

    @Override
    public int compareTo(Hash hash) {
        return this.equals(hash) ? 0 : 1;
    }
}

