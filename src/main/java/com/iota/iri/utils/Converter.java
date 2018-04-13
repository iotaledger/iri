package com.iota.iri.utils;

import java.util.Arrays;

public class Converter {

    public static final int RADIX = 3;
    public static final int BYTE_RADIX = 256;
    public static final int MAX_TRIT_VALUE = (RADIX - 1) / 2, MIN_TRIT_VALUE = -MAX_TRIT_VALUE;

    public static final int NUMBER_OF_TRITS_IN_A_BYTE = 5;
    public static final int NUMBER_OF_TRITS_IN_A_TRYTE = 3;

    static final int[][] BYTE_TO_TRITS_MAPPINGS = new int[243][];
    static final int[][] TRYTE_TO_TRITS_MAPPINGS = new int[27][];

    public static final int HIGH_INTEGER_BITS = 0xFFFFFFFF;
    public static final long HIGH_LONG_BITS = 0xFFFFFFFFFFFFFFFFL;

    public static final String TRYTE_ALPHABET = "9ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static final int MIN_TRYTE_VALUE = -13, MAX_TRYTE_VALUE = 13;


    static {

        final int[] trits = new int[NUMBER_OF_TRITS_IN_A_BYTE];

        for (int i = 0; i < 243; i++) {
            BYTE_TO_TRITS_MAPPINGS[i] = Arrays.copyOf(trits, NUMBER_OF_TRITS_IN_A_BYTE);
            increment(trits, NUMBER_OF_TRITS_IN_A_BYTE);
        }

        for (int i = 0; i < 27; i++) {
            TRYTE_TO_TRITS_MAPPINGS[i] = Arrays.copyOf(trits, NUMBER_OF_TRITS_IN_A_TRYTE);
            increment(trits, NUMBER_OF_TRITS_IN_A_TRYTE);
        }
    }

    public static long longValue(final int[] trits, final int srcPos, final int size) {

        long value = 0;
        for (int i = size; i-- > 0; ) {
            value = value * RADIX + trits[srcPos + i];
        }
        return value;
    }

    public static byte[] allocateBytesForTrits(int tritCount) {
        final int expectedLength = (tritCount + NUMBER_OF_TRITS_IN_A_BYTE - 1) / NUMBER_OF_TRITS_IN_A_BYTE;
        return new byte[expectedLength];
    }

    public static int[] allocateTritsForTrytes(int tryteCount) {
        return new int[tryteCount * NUMBER_OF_TRITS_IN_A_TRYTE];
    }

    public static void bytes(final int[] trits, final int srcPos, byte[] dest, int destPos, final int tritsLength) {

        final int expectedLength = (tritsLength + NUMBER_OF_TRITS_IN_A_BYTE - 1) / NUMBER_OF_TRITS_IN_A_BYTE;

        if ((dest.length - destPos) < expectedLength) {
            throw new IllegalArgumentException("Input array not large enough.");
        }

        for (int i = 0; i < expectedLength; i++) {
            int value = 0;
            for (int j = (tritsLength - i * NUMBER_OF_TRITS_IN_A_BYTE) < 5 ? (tritsLength - i * NUMBER_OF_TRITS_IN_A_BYTE) : NUMBER_OF_TRITS_IN_A_BYTE; j-- > 0; ) {
                value = value * RADIX + trits[srcPos + i * NUMBER_OF_TRITS_IN_A_BYTE + j];
            }
            dest[destPos + i] = (byte) value;
        }
    }

    public static void bytes(final int[] trits, byte[] dest) {
        bytes(trits, 0, dest, 0, trits.length);
    }

    public static void getTrits(final byte[] bytes, final int[] trits) {

        int offset = 0;
        for (int i = 0; i < bytes.length && offset < trits.length; i++) {
            System.arraycopy(BYTE_TO_TRITS_MAPPINGS[bytes[i] < 0 ? (bytes[i] + BYTE_TO_TRITS_MAPPINGS.length) : bytes[i]], 0, trits, offset, trits.length - offset < NUMBER_OF_TRITS_IN_A_BYTE ? (trits.length - offset) : NUMBER_OF_TRITS_IN_A_BYTE);
            offset += NUMBER_OF_TRITS_IN_A_BYTE;
        }
        while (offset < trits.length) {
            trits[offset++] = 0;
        }
    }

    public static void trits(final String trytes, int[] dest, int destOffset) {
        if ((dest.length - destOffset) < trytes.length() * NUMBER_OF_TRITS_IN_A_TRYTE) {
            throw new IllegalArgumentException("Destination array is not large enough.");
        }

        for (int i = 0; i < trytes.length(); i++) {
            System.arraycopy(TRYTE_TO_TRITS_MAPPINGS[TRYTE_ALPHABET.indexOf(trytes.charAt(i))], 0, dest,
                destOffset + i * NUMBER_OF_TRITS_IN_A_TRYTE, NUMBER_OF_TRITS_IN_A_TRYTE);
        }
    }

    public static void copyTrits(final long value, final int[] destination, final int offset, final int size) {

        long absoluteValue = value < 0 ? -value : value;
        for (int i = 0; i < size; i++) {

            int remainder = (int) (absoluteValue % RADIX);
            absoluteValue /= RADIX;
            if (remainder > MAX_TRIT_VALUE) {

                remainder = MIN_TRIT_VALUE;
                absoluteValue++;
            }
            destination[offset + i] = remainder;
        }

        if (value < 0) {
            for (int i = 0; i < size; i++) {
                destination[offset + i] = -destination[offset + i];
            }
        }
    }


    public static String trytes(final int[] trits, final int offset, final int size) {

        final StringBuilder trytes = new StringBuilder();
        for (int i = 0; i < (size + NUMBER_OF_TRITS_IN_A_TRYTE - 1) / NUMBER_OF_TRITS_IN_A_TRYTE; i++) {
            int j = trits[offset + i * 3] + trits[offset + i * 3 + 1] * 3 + trits[offset + i * 3 + 2] * 9;
            if (j < 0) {
                j += TRYTE_ALPHABET.length();
            }
            trytes.append(TRYTE_ALPHABET.charAt(j));
        }
        return trytes.toString();
    }

    public static String trytes(final int[] trits) {
        return trytes(trits, 0, trits.length);
    }

    public static int tryteValue(final int[] trits, final int offset) {
        return trits[offset] + trits[offset + 1] * 3 + trits[offset + 2] * 9;
    }

    public static Pair<int[], int[]> intPair(int[] trits) {
        int[] low = new int[trits.length];
        int[] hi = new int[trits.length];
        for (int i = 0; i < trits.length; i++) {
            low[i] = trits[i] != 1 ? HIGH_INTEGER_BITS : 0;
            hi[i] = trits[i] != -1 ? HIGH_INTEGER_BITS : 0;
        }
        return new Pair<>(low, hi);
    }

    public static Pair<long[], long[]> longPair(int[] trits) {
        long[] low = new long[trits.length];
        long[] hi = new long[trits.length];
        for (int i = 0; i < trits.length; i++) {
            low[i] = trits[i] != 1 ? HIGH_LONG_BITS : 0;
            hi[i] = trits[i] != -1 ? HIGH_LONG_BITS : 0;
        }
        return new Pair<>(low, hi);
    }

    public static void shiftPair(Pair<long[], long[]> source, Pair<long[], long[]> dest) {
        if (source.low.length == dest.low.length && source.hi.length == dest.hi.length) {
            for (int i = 0; i < dest.low.length; i++) {
                dest.low[i] <<= 1;
                dest.low[i] |= source.low[i] & 1;
            }
            for (int i = 0; i < dest.hi.length; i++) {
                dest.hi[i] <<= 1;
                dest.hi[i] |= source.hi[i] & 1;
            }
        }
    }

    public static int[] trits(final Pair<long[], long[]> pair, final int bitIndex) {
        final int length;
        if (pair.low.length == pair.hi.length || pair.low.length < pair.hi.length) {
            length = pair.low.length;
        } else {
            length = pair.hi.length;
        }
        final int[] trits = new int[length];
        long low;
        long hi;
        int mask = 1 << bitIndex;
        for (int i = 0; i < length; i++) {
            low = pair.low[i] & mask;
            hi = pair.hi[i] & mask;
            if (hi == low) {
                trits[i] = 0;
            } else if (low == 0) {
                trits[i] = 1;
            } else if (hi == 0) {
                trits[i] = -1;
            }
        }
        return trits;
    }

    public static int[] trits(long[] low, long[] hi) {
        int[] trits = new int[low.length];
        for (int i = 0; i < trits.length; i++) {
            trits[i] = low[i] == 0 ? 1 : hi[i] == 0 ? -1 : 0;
        }
        return trits;
    }

    public static int[] trits(int[] low, int[] hi) {
        int[] trits = new int[low.length];
        for (int i = 0; i < trits.length; i++) {
            trits[i] = low[i] == 0 ? 1 : hi[i] == 0 ? -1 : 0;
        }
        return trits;
    }

    private static void increment(final int[] trits, final int size) {
        for (int i = 0; i < size; i++) {
            if (++trits[i] > Converter.MAX_TRIT_VALUE) {
                trits[i] = Converter.MIN_TRIT_VALUE;
            } else {
                break;
            }
        }
    }

    public static String asciiToTrytes(String input) {
        StringBuilder sb = new StringBuilder(80);
        for (int i = 0; i < input.length(); i++) {
            int asciiValue = input.charAt(i);
            if (asciiValue > 255) {
                throw new IllegalArgumentException("The ascii trytes String contains un illegal tryte character:" +
                    " char= " + ((char)asciiValue) + ", (" + asciiValue +")");
            }
            int firstValue = asciiValue % 27;
            int secondValue = (asciiValue - firstValue) / 27;
            sb.append(TRYTE_ALPHABET.charAt(firstValue));
            sb.append(TRYTE_ALPHABET.charAt(secondValue));
        }
        return sb.toString();
    }

    public static int[] allocatingTritsFromTrytes(String trytes) {
        int[] trits = allocateTritsForTrytes(trytes.length());
        trits(trytes, trits, 0);
        return trits;
    }
}
