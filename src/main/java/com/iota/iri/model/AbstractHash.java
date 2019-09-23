package com.iota.iri.model;

import com.iota.iri.crypto.Curl;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.model.safe.ByteSafe;
import com.iota.iri.model.safe.TritSafe;
import com.iota.iri.storage.Indexable;
import com.iota.iri.utils.Converter;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Base implementation of a hash object
 */
public abstract class AbstractHash implements Hash, Serializable {
    private final Object lock = new Object();

    private ByteSafe byteSafe;
    private TritSafe tritSafe;

    /**
     * Empty Constructor for a placeholder hash identifier object. Creates a hash identifier object with no properties.
     */
    public AbstractHash() {
    }

    /**
     * Constructor for a hash object using a trit or byte source array. A null safe byte array of the
     * same size as the source will be created, storing a copy of the input values in the object.
     *
     * @param source A byte array containing the source transaction information in either trit or byte format
     * @param sourceOffset The offset defining the start point for the hash object in the source
     * @param sourceSize The size of the hash object that will be created
     */
    public AbstractHash(byte[] source, int sourceOffset, int sourceSize) {
        if(sourceSize < SIZE_IN_TRITS) {
            byte[] dest = new byte[SIZE_IN_BYTES];
            System.arraycopy(source, sourceOffset, dest, 0, Math.min(dest.length, Math.min(source.length, sourceSize)));
            this.byteSafe = new ByteSafe(dest);
        } else {
            byte[] dest = new byte[SIZE_IN_TRITS];
            System.arraycopy(source, sourceOffset, dest, 0, dest.length);
            this.tritSafe = new TritSafe(dest);
        }
    }

    /**
     * Assigns the input byte data to the hash object. Each hash object can only be initialized with data
     * once.If the byte or trit arrays are not null, an <tt>IllegalStateException</tt> is thrown.
     *
     * @param bytes A byte array containing the source bytes
     */
    @Override
    public void read(byte[] bytes) {
        if (bytes != null) {
            synchronized (lock) {
                if (byteSafe != null || tritSafe != null) {
                    throw new IllegalStateException("I cannot be initialized with data twice.");
                }
                byte[] dest = new byte[SIZE_IN_BYTES];
                System.arraycopy(bytes, 0, dest, 0, Math.min(dest.length, bytes.length));
                byteSafe = new ByteSafe(dest);
            }
        }
    }

    /**
     * Checks if the hash object is storing a byte array. If the object's byte array is <tt>null</tt>,
     * then a byte array conversion of the object's trit array will be created and stored. If there
     * is no trit array present, a <tt>NullPointerException</tt> will be thrown.
     *
     * @return The stored byte array containing the hash values
     */
    @Override
    public byte[] bytes() {
        ByteSafe safe = byteSafe;
        if (safe == null) {
            synchronized (lock) {
                if (byteSafe == null) {
                    Objects.requireNonNull(tritSafe, "I need my trits to be initialized in order to construct bytes.");
                    byte[] src = trits();
                    byte[] dest = new byte[SIZE_IN_BYTES];
                    Converter.bytes(src, 0, dest, 0, src.length);
                    byteSafe = new ByteSafe(dest);
                }
                safe = byteSafe;
            }
        }
        return safe.getData();
    }

    /**
     * Checks if the hash object is storing a trit array. If the object's trit array is <tt>null</tt>,
     * then a trit array conversion of the object's byte array will be created. If there is no byte
     * array present, a <tt>NullPointerException</tt> will be thrown.
     *
     * @return The stored trit array containing the hash values
     */
    @Override
    public byte[] trits() {
        TritSafe safe = tritSafe;
        if (safe == null) {
            synchronized (lock) {
                if (tritSafe == null) {
                    Objects.requireNonNull(byteSafe, "I need my bytes to be initialized in order to construct trits.");
                    byte[] src = bytes();
                    byte[] dest = new byte[Curl.HASH_LENGTH];
                    Converter.getTrits(src, dest);
                    tritSafe = new TritSafe(dest);
                }
                safe = tritSafe;
            }
        }
        return safe.getData();
    }

    /**
     * @return The number of zero value trits at the end of the hash object's trit array
     */
    @Override
    public int trailingZeros() {
        byte[] trits = trits();
        int index = SIZE_IN_TRITS;
        int zeros = 0;
        while (index-- > 0 && trits[index] == 0) {
            zeros++;
        }
        return zeros;
    }

    @Override
    public Indexable incremented() {
        return null;
    }

    @Override
    public Indexable decremented() {
        return null;
    }

    @Override
    public int hashCode() {
        bytes();
        return byteSafe.getHashcode();
    }

    @Override
    public String toString() {
        return Converter.trytes(trits());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Hash hash = (Hash) obj;
        return Arrays.equals(bytes(), hash.bytes());
    }

    @Override
    public int compareTo(Indexable indexable) {
        Hash hash = (indexable instanceof Hash) ? (Hash) indexable : HashFactory.GENERIC.create(Transaction.class, indexable.bytes());
        if (this.equals(hash)) {
            return 0;
        }
        long diff = Converter.longValue(hash.trits(), 0, SIZE_IN_TRITS) - Converter.longValue(trits(), 0, SIZE_IN_TRITS);
        if (Math.abs(diff) > Integer.MAX_VALUE) {
            return diff > 0L ? Integer.MAX_VALUE : Integer.MIN_VALUE + 1;
        }
        return (int) diff;
    }
}
