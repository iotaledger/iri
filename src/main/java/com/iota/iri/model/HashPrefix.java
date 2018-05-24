package com.iota.iri.model;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;

public final class HashPrefix implements HashId {
    public static final int PREFIX_LENGTH = 16;
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
}
