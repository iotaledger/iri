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

    public static int getInteger(byte[] bytes) {
        return getInteger(bytes, 0);
    }
    public static int getInteger(byte[] bytes, int start) {
        if(bytes == null) {
            return 0;
        }
        int length = Integer.BYTES;
        int res = 0;
        for (int i=0; i< length;i++) {
            res |= (bytes[start + i] & 0xFFL) << ((length-i-1) * 8);
        }
        return res;
    }
}
