package com.iota.iri.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

import com.iota.iri.crypto.Curl;
import com.iota.iri.model.persistables.Transaction;
import com.iota.iri.model.safe.ByteSafe;
import com.iota.iri.model.safe.TritSafe;
import com.iota.iri.storage.Indexable;
import com.iota.iri.utils.Converter;

public abstract class AbstractHash implements Hash, Serializable {
    private final Object lock = new Object();

    private ByteSafe byteSafe;
    private TritSafe tritSafe;

    public AbstractHash(byte[] source, int sourceOffset, int sourceSize) {
        if(sourceSize < SIZE_IN_TRITS) {
            byte[] dest = new byte[SIZE_IN_BYTES];
            System.arraycopy(source, sourceOffset, dest, 0, sourceSize - sourceOffset > source.length ? source.length - sourceOffset : sourceSize);
            this.byteSafe = new ByteSafe(dest);
        } else {
            byte[] dest = new byte[SIZE_IN_TRITS];
            System.arraycopy(source, sourceOffset, dest, 0, dest.length);
            this.tritSafe = new TritSafe(dest);
        }
    }

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
