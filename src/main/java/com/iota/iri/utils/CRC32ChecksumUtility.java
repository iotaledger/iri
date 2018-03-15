package com.iota.iri.utils;

import java.util.zip.CRC32;

public final class CRC32ChecksumUtility {

    private final static int CRC32_BYTES = 16;


    public static byte[] makeCheckumBytes(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        StringBuilder sb = new StringBuilder(Long.toHexString(crc32.getValue()));
        while (sb.length() < CRC32_BYTES) {
            sb.insert(0, "0");
        }
        byte[] crc32Bytes = sb.toString().getBytes();
        if (crc32Bytes.length != CRC32_BYTES) {
            throw new RuntimeException("CRC32 BYTES LENGTH IS WRONG");
        }
        return crc32Bytes;
    }
}
