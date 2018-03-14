package com.iota.iri.utils;

import com.iota.iri.network.Node;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.zip.CRC32;

public class CRCCheckumUtility {

    private static final String PADDING = StringUtils.repeat("0", Node.CRC32_BYTES);

    public static byte[] createCRC32CheckumBytes(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data, 0, Node.TRANSACTION_PACKET_SIZE);
        String hex = Long.toHexString(crc32.getValue());
        String crc32Str = PADDING.substring(0,Node.CRC32_BYTES - hex.length());
        return (crc32Str + hex).getBytes();
    }

    public static boolean isOK(byte[] data, byte[] expectedCRC32Checksum) {
        byte[] result = createCRC32CheckumBytes(data);
        return Arrays.equals(expectedCRC32Checksum, result);
    }

    public static boolean isOK(byte[] dataInclChecksum) {
        byte[] result = createCRC32CheckumBytes(dataInclChecksum);
        for (int i = 0; i < Node.CRC32_BYTES; i++) {
            if (dataInclChecksum[Node.TRANSACTION_PACKET_SIZE + i] != result[i]) {
                return false;
            }
        }
        return true;
    }


}
