package com.iota.iri.model;

import com.iota.iri.crypto.Curl;
import com.iota.iri.utils.Converter;

import java.util.Arrays;

/**
 * Creates a Prefix Hash object with a byte array length of {@value PREFIX_LENGTH}.
 */
public final class HashPrefix implements HashId {
    public static final int PREFIX_LENGTH = 44;
    private final byte[] bytes;

    /**
     * Checks if the given hash Id is an instance of a Hash Prefix object. If it is, the Hash Prefix object
     * is returned, otherwise a new Hash Prefix object is created from the given hash Id's stored bytes.
     *
     * @param hashId The hash Id that a new Prefix will be generated from
     * @return  A Prefix of the given hash Id
     */
    public static HashPrefix createPrefix(HashId hashId) {
        if (hashId == null) {
            return null;
        }
        if (hashId instanceof HashPrefix) {
            return (HashPrefix) hashId;
        }

        byte[] bytes = hashId.bytes();
        bytes = Arrays.copyOf(bytes, PREFIX_LENGTH);
        return new HashPrefix(bytes);
    }

    /**
     * Generates a Hash Prefix object using the provided byte array source
     * @param bytes The source in bytes
     */
    private HashPrefix(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public byte[] bytes() {
        return bytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HashPrefix that = (HashPrefix) o;
        return Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return trytes(bytes);
    }

    /**
     * Converts the given byte array to a tryte string
     *
     * @param bytes The input bytes to be converted
     * @return The converted tryte string
     */
    private static String trytes(byte[] bytes) {
        byte[] dest = new byte[Curl.HASH_LENGTH];
        Converter.getTrits(bytes, dest);
        return Converter.trytes(dest);
    }
}
