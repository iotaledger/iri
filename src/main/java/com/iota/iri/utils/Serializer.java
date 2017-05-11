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
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(bytes, start, Long.BYTES);
        buffer.flip();
        return buffer.getLong();
    }
    public static int getInteger(byte[] bytes) {
        return getInteger(bytes, 0);
    }
    public static int getInteger(byte[] bytes, int start) {
        if(bytes == null) return 0;
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.put(bytes, start, Integer.BYTES);
        buffer.flip();
        return buffer.getInt();
    }
    public static byte[] serialize(byte value) {
        return new byte[]{value};
    }
    public static byte[] serialize(byte[] bytes) {
        return bytes;
    }
}
