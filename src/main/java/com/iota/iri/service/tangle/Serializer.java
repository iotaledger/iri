package com.iota.iri.service.tangle;

import java.io.*;
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
        if(bytes == null || bytes.length != Integer.BYTES) return 0;
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(bytes);
        buffer.flip();
        return buffer.getLong();
    }
    public static int getInteger(byte[] bytes) {
        if(bytes == null || bytes.length != Integer.BYTES) return 0;
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.put(bytes);
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
