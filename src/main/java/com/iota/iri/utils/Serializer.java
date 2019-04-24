package com.iota.iri.utils;

/**
 * Created by paul on 3/13/17 for iri-testnet.
 */
public class Serializer {
    public static byte[] serialize(long value) {
    	byte[] result = new byte[Long.BYTES];
        for (int i = Long.BYTES - 1; i >= 0; i--) {
            result[i] = (byte)(value & 0xFF);
            value >>= 8;
        }
        return result;
    }
    
    public static byte[] serialize(int integer) {
    	byte[] result = new byte[Integer.BYTES];
        for (int i = Integer.BYTES - 1; i >= 0; i--) {
            result[i] = (byte)(integer & 0xFF);
            integer >>= 8;
        }
        return result;
    }
    
    public static long getLong(byte[] bytes) {
        return getLong(bytes, 0);
    }
    public static long getLong(byte[] bytes, int start) {
        if(bytes == null) {
            return 0;
        }
        int length = Long.BYTES;
        long res = 0;
        for (int i=0; i< length;i++) {
            res |= (bytes[start + i] & 0xFFL) << ((length-i-1) * 8);
        }
        return res;
    }

    /**
     * Reads the default amount of bytes for an int(4) and turns it into an int.
     * Starts at the beginning of the array
     * 
     * @param bytes The bytes we use to make an int
     * @return The created int, or 0 when bytes is <code>null</code>
     */
    public static int getInteger(byte[] bytes) {
        return getInteger(bytes, 0);
    }
    
    /**
     * Reads the default amount of bytes for an int(4) and turns it into an int.
     * 
     * @param bytes The bytes we use to make an int
     * @param start The point in the array from which we start reading
     * @return The created int, or 0 when bytes is <code>null</code>
     */
    public static int getInteger(byte[] bytes, int start) {
        return getInteger(bytes, start, Integer.BYTES);
    }
    
    /**
     * Reads the bytes for the given length, starting at the starting point given.
     * 
     * @param bytes The bytes we use to make an int
     * @param start The point in the array from which we start reading
     * @param length Amount of bytes to read
     * @return The created int, or 0 when bytes is <code>null</code>
     */
    public static int getInteger(byte[] bytes, int start, int length) {
        if(bytes == null) {
            return 0;
        }
        int res = 0;
        for (int i=0; i< length;i++) {
            res |= (bytes[start + i] & 0xFFL) << ((length-i-1) * 8);
        }
        return res;
    }
}
