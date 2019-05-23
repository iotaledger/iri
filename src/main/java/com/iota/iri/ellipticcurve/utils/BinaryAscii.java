package com.iota.iri.ellipticcurve.utils;
import java.math.BigInteger;
import java.util.Arrays;


public final class BinaryAscii {

    public static String hexFromBinary(ByteString string) {
        return hexFromBinary(string.getBytes());
    }

    public static String hexFromBinary(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();

        for (byte aByte : bytes) {
            String hex = Integer.toHexString(0xFF & aByte);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static byte[] binaryFromHex(String string) {
        byte[] bytes = new BigInteger(string, 16).toByteArray();
        int i = 0;
        while (i < bytes.length && bytes[i] == 0) {
            i++;
        }
        return Arrays.copyOfRange(bytes, i, bytes.length);
    }

    public static byte[] toBytes(int c) {
        return new byte[]{(byte) c};
    }

    /**
     * Get a number representation of a string
     *
     * @param string String to be converted in a number
     * @return Number in hex from string
     */
    public static BigInteger numberFromString(byte[] string) {
        return new BigInteger(BinaryAscii.hexFromBinary(string), 16);
    }

    /**
     * Get a string representation of a number
     *
     * @param number number to be converted in a string
     * @param length length max number of character for the string
     * @return hexadecimal string
     */
    public static ByteString stringFromNumber(BigInteger number, int length) {
        String fmtStr = "%0" + String.valueOf(2 * length) + "x";
        String hexString = String.format(fmtStr, number);
        return new ByteString(BinaryAscii.binaryFromHex(hexString));
    }
}
