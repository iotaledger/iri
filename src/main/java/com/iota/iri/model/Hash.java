package com.iota.iri.model;

import com.iota.iri.hash.Curl;
import com.iota.iri.utils.Converter;
import com.sun.istack.internal.NotNull;

import java.io.Serializable;
import java.util.Arrays;

import static com.iota.iri.utils.Converter.NUMBER_OF_TRITS_IN_A_BYTE;
import static com.iota.iri.utils.Converter.NUMBER_OF_TRITS_IN_A_TRYTE;

public class Hash implements Comparable<Hash>, Serializable{

    public static final int SIZE_IN_TRITS = 243;
    public static final int SIZE_IN_BYTES = 49;

    public static final Hash NULL_HASH = new Hash(new int[Curl.HASH_LENGTH]);

    private byte[] bytes;
    private int[] trits;
    private int hashCode;
    
    // constructors' bill

    public Hash(final byte[] bytes, final int offset, final int size) {
        this.bytes = new byte[SIZE_IN_BYTES];
        System.arraycopy(bytes, offset, this.bytes, 0, size - offset > bytes.length ? bytes.length-offset: size);
        hashCode = Arrays.hashCode(this.bytes);
    }

    public Hash(final byte[] bytes) {
        this(bytes, 0, SIZE_IN_BYTES);
    }

    public Hash(final int[] trits, final int offset) {
        this.trits = new int[SIZE_IN_TRITS];
        System.arraycopy(trits, 0, this.trits, 0, SIZE_IN_TRITS);
        //this(Converter.bytes(trits, offset, trits.length));
    }

    public Hash(final int[] trits) {
        this(trits, 0);
    }

    public Hash(final String trytes) {
        this(Converter.trits(trytes));
    }

    //
    /*
    public static Hash calculate(byte[] bytes) {
        return calculate(bytes, SIZE_IN_TRITS, new Curl());
    }
    */
    public static Hash calculate(int[] trits) {
        return calculate(trits, 0, trits.length, new Curl());
    }

    public static Hash calculate(byte[] bytes, int tritsLength, final Curl curl) {
        int[] trits = new int[tritsLength];
        Converter.getTrits(bytes, trits);
        return calculate(trits, 0, tritsLength, curl);
    }
    public static Hash calculate(final int[] tritsToCalculate, int offset, int length, @NotNull final Curl curl) {
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
        if(trits == null) {
            trits = new int[Curl.HASH_LENGTH];
            Converter.getTrits(bytes, trits);
        }
        return trits;
    }

    @Override
    public boolean equals(final Object obj) {
        assert obj instanceof Hash;
        if (obj == null) return false;
        return Arrays.equals(bytes(), ((Hash) obj).bytes());
    }

    @Override
    public int hashCode() {
        if(bytes == null) {
            bytes();
        }
        return hashCode;
    }

    @Override
    public String toString() {
        return Converter.trytes(trits());
    }
    
    public byte[] bytes() {
        if(bytes == null) {
            bytes = Converter.bytes(trits);
            hashCode = Arrays.hashCode(this.bytes);
        }
		return bytes;
	}

    @Override
    public int compareTo(Hash hash) {
        if(this.equals(hash)) {
            return 0;
        }
        long diff = Converter.longValue(hash.trits(), 0, SIZE_IN_TRITS) - Converter.longValue(trits(), 0, SIZE_IN_TRITS);
        if(Math.abs(diff) > Integer.MAX_VALUE) {
            return diff > 0L ? Integer.MAX_VALUE : Integer.MIN_VALUE + 1;
        }
        return (int) diff;
    }
}

