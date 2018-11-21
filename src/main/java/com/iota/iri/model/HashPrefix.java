package com.iota.iri.model;

import com.iota.iri.crypto.Curl;
import com.iota.iri.utils.Converter;

import java.util.Arrays;

public final class HashPrefix implements HashId {
    public static final int PREFIX_LENGTH = 44;
    private final byte[] bytes;

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

    private static String trytes(byte[] bytes) {
        byte[] dest = new byte[Curl.HASH_LENGTH];
        Converter.getTrits(bytes, dest);
        return Converter.trytes(dest);
    }
}
