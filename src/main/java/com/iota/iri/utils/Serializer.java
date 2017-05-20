package com.iota.iri.utils;

import java.nio.ByteBuffer;

/**
 * Created by paul on 3/13/17 for iri-testnet.
 */
public class Serializer {
    public static byte[] serialize(Long value) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(value);
        return buffer.array();
    }
    public static byte[] serialize(int integer) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(integer);
        return buffer.array();
    }
    public static long getLong(byte[] bytes) {
        return getLong(bytes, 0);
    }
    public static long getLong(byte[] bytes, int start) {
        if(bytes == null) return 0;
        int length = Long.BYTES;
        long res = 0;
        for (int i=0; i< length;i++) {
            res |= (bytes[start + i] & 0xFFL) << ((length-i-1) * 8);
        }
        return res;
    }

    public static int getInteger(byte[] bytes) {
        return getInteger(bytes, 0);
    }
    public static int getInteger(byte[] bytes, int start) {
        if(bytes == null) return 0;
        int length = Integer.BYTES;
        int res = 0;
        for (int i=0; i< length;i++) {
            res |= (bytes[start + i] & 0xFFL) << ((length-i-1) * 8);
        }
        return res;
    }

    public static byte[] serialize(byte value) {
        return new byte[]{value};
    }
    public static byte[] serialize(byte[] bytes) {
        return bytes;
    }
}
