package com.iota.iri.utils;

import com.iota.iri.hash.keys.Tuple;

import java.util.Arrays;
import java.util.stream.IntStream;

public class Converter {

	public static final int RADIX = 3;
	public static final int MAX_TRIT_VALUE = (RADIX - 1) / 2, MIN_TRIT_VALUE = -MAX_TRIT_VALUE;

    public static final int NUMBER_OF_TRITS_IN_A_BYTE = 5;
    public static final int NUMBER_OF_TRITS_IN_A_TRYTE = 3;
    
    static final int[][] BYTE_TO_TRITS_MAPPINGS = new int[243][];
    static final int[][] TRYTE_TO_TRITS_MAPPINGS = new int[27][];
    
    public static final String TRYTE_ALPHABET = "9ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    
    public static final int MIN_TRYTE_VALUE = -13, MAX_TRYTE_VALUE = 13;

    private static final int HIGH_BITS = 0b11111111111111111111111111111111;
    private static final int LOW_BITS = 0b00000000000000000000000000000000;

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

    public static long longValue(final int[] trits, final int offset, final int size) {

        long value = 0;
        for (int i = size; i-- > 0; ) {
            value = value * RADIX + trits[offset + i];
        }
        return value;
    }


    public static Tuple[] tuple(int[] trits) {
        Tuple[] tuples = new Tuple[trits.length];
        for(int i = 0; i < trits.length; i++) {
            tuples[i] = new Tuple(trits[i]);
        }
        return tuples;
    }

    public static int[] trits(Tuple[] tuples, int index) {
        assert index < 32;
        int[] trits = new int[tuples.length];
        for(int i = 0; i < tuples.length; i++) {
            trits[i] = tuples[i].value(index);
        }
        return trits;
    }
    public static int[] trits(Tuple[] tuples) {
        return trits(tuples, 0);
    }
    public static String[] trytes(final Tuple[] tuples, int bitIndex) {
        return IntStream.range(0, bitIndex).parallel().mapToObj(i ->
            trytes(IntStream.range(0, tuples.length).parallel()
                    .map(j -> tuples[j].value(i))
                    .toArray())
        ).toArray(String[]::new);
        //return trytes(trits(tuples));
    }

    public static byte[] bytes(final int[] trits, final int offset, final int size) {

        final byte[] bytes = new byte[(size + NUMBER_OF_TRITS_IN_A_BYTE - 1) / NUMBER_OF_TRITS_IN_A_BYTE];
        for (int i = 0; i < bytes.length; i++) {

            int value = 0;
            for (int j = (size - i * NUMBER_OF_TRITS_IN_A_BYTE) < 5 ? (size - i * NUMBER_OF_TRITS_IN_A_BYTE) : NUMBER_OF_TRITS_IN_A_BYTE; j-- > 0; ) {
                value = value * RADIX + trits[offset + i * NUMBER_OF_TRITS_IN_A_BYTE + j];
            }
            bytes[i] = (byte)value;
        }

        return bytes;
    }

    public static byte[] bytes(final int[] trits) {
        return bytes(trits, 0, trits.length);
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

    public static int[] trits(final String trytes) {

        final int[] trits = new int[trytes.length() * NUMBER_OF_TRITS_IN_A_TRYTE];
        for (int i = 0; i < trytes.length(); i++) {
            System.arraycopy(TRYTE_TO_TRITS_MAPPINGS[TRYTE_ALPHABET.indexOf(trytes.charAt(i))], 0, trits, i * NUMBER_OF_TRITS_IN_A_TRYTE, NUMBER_OF_TRITS_IN_A_TRYTE);
        }
        return trits;
    }

    public static void copyTrits(final long value, final int[] destination, final int offset, final int size) {

        long absoluteValue = value < 0 ? -value : value;
        for (int i = 0; i < size; i++) {

            int remainder = (int)(absoluteValue % RADIX);
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

    public static void increment(final int[] trits, final int size) {
        for (int i = 0; i < size; i++) {
            if (++trits[i] > Converter.MAX_TRIT_VALUE) {
                trits[i] = Converter.MIN_TRIT_VALUE;
            } else {
                break;
            }
        }
    }
}
