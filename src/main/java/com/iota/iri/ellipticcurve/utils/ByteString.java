package com.iota.iri.ellipticcurve.utils;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**
 * Company: SFL LLC
 * Created on 20-Jan-19
 *
 * @author Taron Petrosyan
 */
public class ByteString {
    private byte[] bytes;

    public ByteString() {
        bytes = new byte[]{};
    }

    public ByteString(byte[] bytes) {
        this.bytes = bytes;
    }

    public short getShort(int index) {
        return (short) (bytes[index] & 0xFF);
    }

    public ByteString substring(int start) {
        return substring(start, bytes.length);
    }

    public ByteString substring(int start, int end) {
        if (end > bytes.length) {
            end = bytes.length;
        }
        if (end < 0) {
            end = bytes.length - end;
        }
        if (start > end) {
            return new ByteString();
        }

        return new ByteString(Arrays.copyOfRange(bytes, start, end));
    }

    public byte[] getBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    public int length() {
        return bytes.length;
    }

    public boolean isEmpty() {
        return bytes.length == 0;
    }

    public void insert(byte[] b) {
        this.insert(bytes.length, b);
    }

    public void insert(int index, byte[] b) {
        byte[] result = new byte[b.length + bytes.length];
        System.arraycopy(bytes, 0, result, 0, index);
        System.arraycopy(b, 0, result, index, b.length);
        if (index < bytes.length) {
            System.arraycopy(bytes, index, result, b.length + index, bytes.length - index);
        }
        this.bytes = result;
    }

    public void replace(int index, byte value) {
        bytes[index] = value;
    }


    @Override
    public String toString() {
        if (bytes.length == 0) {
            return "";
        }
        try {
            return new String(bytes, "ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException();
        }
    }
}
