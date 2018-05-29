package com.iota.iri.utils;

import com.iota.iri.model.Hash;
import com.iota.iri.storage.Indexable;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class IotaUtils {
    /**
     * Used to create low-memory index keys.
     *
     * @param hash the hash we create the key from
     * @param length the length of the desired subhash
     * @return a {@link ByteBuffer} that holds a subarray of {@link Hash#bytes()}
     * that has the specified {@code length}
     */
    public static ByteBuffer getHashPrefix(Indexable hash, int length) {
        if (hash == null) {
            return null;
        }

        return ByteBuffer.wrap(Arrays.copyOf(hash.bytes(), length));
    }
}
