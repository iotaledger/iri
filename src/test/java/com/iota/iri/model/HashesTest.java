package com.iota.iri.model;

import org.apache.commons.lang3.ArrayUtils;
import static org.junit.Assert.*;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.OperationNotSupportedException;
import java.util.List;

public class HashesTest {

    private static final int SPLIT_LENGTH = 127;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Test
    public void bytesTest() {
        Hashes hashes = TestModelUtils.createAddresses(0);
        assertArrayEquals("Should serialize to an empty array",
                new byte[0], hashes.bytes());


        hashes = TestModelUtils.createAddresses(3 * Hash.SIZE_IN_BYTES);
        byte[] serializedBytes = hashes.bytes();
        log.debug("current bytes serialization is {}", serializedBytes);
        byte[] legacySerialization = legacySerializeBytes(hashes);
        log.debug("legacy bytes serialization is {}", legacySerialization);
        assertArrayEquals("hashes isn't being serialized as expected",
                legacySerialization, serializedBytes);
    }

    @Test
    public void splitTest() throws OperationNotSupportedException {
        Hashes hashes = TestModelUtils.createAddresses(16 * SPLIT_LENGTH);
        List<byte[]> splitHashes = hashes.splitBytes(SPLIT_LENGTH);
        //Test can break if the value of Hash.SIZE_IN_BYTES changes
        assertEquals("The list of splitBytes hashes does not have the expected size",
                21 , splitHashes.size());

        byte[] serializedSplitBytes = splitHashes.stream()
                .reduce((a, b) -> ArrayUtils.addAll(a, b))
                .orElse(new byte[0]);
        log.debug("serialized splitBytes: {}", serializedSplitBytes);

        byte[] bytes = hashes.bytes();
        log.debug("serialized bytes: {}", bytes);

        assertArrayEquals("splitHashes do not serialize to the same output as as hashes",
                bytes, serializedSplitBytes);
    }

    private byte[] legacySerializeBytes (Hashes hashes) {
        return hashes.set.parallelStream()
                .map(Hash::bytes)
                .reduce((a,b) -> ArrayUtils.addAll(ArrayUtils.add(a, Hashes.DELIMITER), b))
                .orElse(new byte[0]);
    }
}
