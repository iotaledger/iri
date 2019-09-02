package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;
import com.iota.iri.model.HashFactory;
import com.iota.iri.storage.Persistable;
import org.apache.commons.lang3.ArrayUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents a persistable set of {@link Hash} to act as a container for hash identifiers. These are
 * used in our persistence layer to store the link of one key to a set of keys in the database.
 *
 * <p>
 *     A <tt>Hash</tt> set contains the byte array representations of Hash objects.
 *     These hashes serve as reference points for specific parts of a transaction set,
 *     including the <tt>Address</tt>, <tt>Tag</tt>, <tt>Branch</tt> and <tt>Trunk</tt>
 *     transaction components.
 * </p>
 */
public class Hashes implements Persistable {

    /**A set for storing hashes*/
    public Set<Hash> set = new LinkedHashSet<>();

    /**A delimeter for separating hashes within a byte stream*/
    private static final byte delimiter = ",".getBytes()[0];

    /**Returns the bytes of the contained hash set*/
    @Override
    public byte[] bytes() {
        return set.parallelStream()
                .map(Hash::bytes)
                .reduce((a,b) -> ArrayUtils.addAll(ArrayUtils.add(a, delimiter), b))
                .orElse(new byte[0]);
    }

    /**
     * Reads the given byte array. If the array is not null, the {@link Hash} objects will be added to
     * the collection.
     *
     * @param bytes the byte array that will be read
     */
    @Override
    public void read(byte[] bytes) {
        if(bytes != null) {
            set = new LinkedHashSet<>(bytes.length / (1 + Hash.SIZE_IN_BYTES) + 1);
            for (int i = 0; i < bytes.length; i += 1 + Hash.SIZE_IN_BYTES) {
                set.add(HashFactory.TRANSACTION.create(bytes, i, Hash.SIZE_IN_BYTES));
            }
        }
    }

    @Override
    public byte[] metadata() {
        return new byte[0];
    }

    @Override
    public void readMetadata(byte[] bytes) {

    }


    @Override
    public boolean canMerge() {
        return true;
    }

    @Override
    public Persistable mergeInto(Persistable source){
        if(source instanceof Hashes){
            Set<Hash> setTwo = ((Hashes) source).set;
            set.addAll(setTwo);
            return this;
        }

        return null;
    }

    @Override
    public boolean exists() {
        return !set.isEmpty();
    }
}
