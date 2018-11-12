package com.iota.iri.utils;

import java.util.Arrays;

/**
 * Converts between different representations of trytes, bytes, trits and numbers.
 *
 * <p>
 *     The IOTA protocol is represented in Trits and Trytes, while data is stored and broadcast in Bytes.
 *     This class is used to convert between the representations.
 * </p>
 * <p>
 *     This class also provides additional conversion tools:
 *     <ul>
 *         <li>Converting Trits to numerical values based on <i>Balanced ternary</i> arithmetic</li>
 *         <li>Converting ascii to a Tryte string</li>
 *     </ul>
 * </p>
 */
public class Converter {

	public static final int RADIX = 3;
    public static final int BYTE_RADIX = 256;
	public static final int MAX_TRIT_VALUE = (RADIX - 1) / 2, MIN_TRIT_VALUE = -MAX_TRIT_VALUE;

    public static final int NUMBER_OF_TRITS_IN_A_BYTE = 5;
    public static final int NUMBER_OF_TRITS_IN_A_TRYTE = 3;

    static final byte[][] BYTE_TO_TRITS_MAPPINGS = new byte[243][];
    static final byte[][] TRYTE_TO_TRITS_MAPPINGS = new byte[27][];

    public static final int HIGH_INTEGER_BITS = 0xFFFFFFFF;
    public static final long HIGH_LONG_BITS = 0xFFFFFFFFFFFFFFFFL;

    public static final String TRYTE_ALPHABET = "9ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static final int MIN_TRYTE_VALUE = -13, MAX_TRYTE_VALUE = 13;

    //lookup tables for bytes->trits and trytes->trits conversion
    static {

        final byte[] trits = new byte[NUMBER_OF_TRITS_IN_A_BYTE];

        for (int i = 0; i < 243; i++) {
            BYTE_TO_TRITS_MAPPINGS[i] = Arrays.copyOf(trits, NUMBER_OF_TRITS_IN_A_BYTE);
            increment(trits, NUMBER_OF_TRITS_IN_A_BYTE);
        }

        for (int i = 0; i < 27; i++) {
            TRYTE_TO_TRITS_MAPPINGS[i] = Arrays.copyOf(trits, NUMBER_OF_TRITS_IN_A_TRYTE);
            increment(trits, NUMBER_OF_TRITS_IN_A_TRYTE);
        }

    }
    private static void increment(final byte[] trits, final int size) {
        for (int i = 0; i < size; i++) {
            if (++trits[i] > Converter.MAX_TRIT_VALUE) {
                trits[i] = Converter.MIN_TRIT_VALUE;
            } else {
                break;
            }
        }
    }

    // Bytes <-> Trits

    /**
     * Allocates a byte array large enough to fit trits, used for converting trits to bytes.
     *
     * @param tritCount length of trit array (to be converted)
     * @return a new byte array large enough to fit trits
     */
    public static byte[] allocateBytesForTrits(int tritCount) {
        final int expectedLength = (tritCount + NUMBER_OF_TRITS_IN_A_BYTE - 1) / NUMBER_OF_TRITS_IN_A_BYTE;
        return new byte[expectedLength];
    }

    /**
     * Converts trits array to bytes array based on {@link #NUMBER_OF_TRITS_IN_A_BYTE}.<br>
     *     this method will override the content of {@code dest}
     *
     * @param trits source trits array
     * @param srcPos starting position for trits
     * @param dest destination bytes array
     * @param destPos starting position for destination bytes array
     * @param tritsLength amount of trits to convert
     */
    public static void bytes(final byte[] trits, final int srcPos, byte[] dest, int destPos, final int tritsLength) {

        final int expectedLength = (tritsLength + NUMBER_OF_TRITS_IN_A_BYTE - 1) / NUMBER_OF_TRITS_IN_A_BYTE;

        if((dest.length - destPos) < expectedLength) {
            throw new IllegalArgumentException("Input array not large enough.");
        }

        for (int i = 0; i < expectedLength; i++) {
            int value = 0;
            for (int j = (tritsLength - i * NUMBER_OF_TRITS_IN_A_BYTE) < 5 ? (tritsLength - i * NUMBER_OF_TRITS_IN_A_BYTE) : NUMBER_OF_TRITS_IN_A_BYTE; j-- > 0; ) {
                value = value * RADIX + trits[srcPos + i * NUMBER_OF_TRITS_IN_A_BYTE + j];
            }
            dest[destPos + i] = (byte)value;
        }
    }

    /**
     * Converts trits array to bytes array. <br>
     * this method will override the content of {@code dest}
     * @see #bytes(byte[], int, byte[], int, int)
     *
     * @param trits source trits array
     * @param dest destination bytes array
     */
    public static void bytes(final byte[] trits, byte[] dest) {
        bytes(trits, 0, dest, 0, trits.length);
    }

    /**
     * Allocates trits (a byte array) large enough to fit trytes, used for converting trytes to trits.
     *
     * @param tryteCount length of tryte array (to be converted)
     * @return a new byte array large enough to fit trytes
     */
    public static byte[] allocateTritsForTrytes(int tryteCount) {
        return new byte[tryteCount * NUMBER_OF_TRITS_IN_A_TRYTE];
    }

    /**
     * Converts bytes array to trits array based on {@link #NUMBER_OF_TRITS_IN_A_BYTE}.<br>
     *     the inverse of {@link #bytes(byte[], int, byte[], int, int)}. <br>
     *     this method will override the content of {@code trits}
     *
     * @param bytes source bytes array
     * @param trits destination trits array
     */
    public static void getTrits(final byte[] bytes, final byte[] trits) {

        int offset = 0;
        for (int i = 0; i < bytes.length && offset < trits.length; i++) {
            System.arraycopy(BYTE_TO_TRITS_MAPPINGS[bytes[i] < 0 ? (bytes[i] + BYTE_TO_TRITS_MAPPINGS.length) : bytes[i]], 0, trits, offset, trits.length - offset < NUMBER_OF_TRITS_IN_A_BYTE ? (trits.length - offset) : NUMBER_OF_TRITS_IN_A_BYTE);
            offset += NUMBER_OF_TRITS_IN_A_BYTE;
        }
        while (offset < trits.length) {
            trits[offset++] = 0;
        }
    }



    // Trytes <-> Trits
    /**
     * Converts a tryte string to trits (bytes array) based on {@link #NUMBER_OF_TRITS_IN_A_TRYTE}.<br>
     *     this method will override the content of {@code dest}
     *
     * @param trytes source trytes string
     * @param dest destination trits array
     * @param destOffset starting position for destination bytes array
     */
    public static void trits(final String trytes, byte[] dest, int destOffset) {
        if((dest.length - destOffset) < trytes.length() * NUMBER_OF_TRITS_IN_A_TRYTE) {
            throw new IllegalArgumentException("Destination array is not large enough.");
        }

        for (int i = 0; i < trytes.length(); i++) {
            System.arraycopy(TRYTE_TO_TRITS_MAPPINGS[TRYTE_ALPHABET.indexOf(trytes.charAt(i))], 0, dest,
                    destOffset + i * NUMBER_OF_TRITS_IN_A_TRYTE, NUMBER_OF_TRITS_IN_A_TRYTE);
        }
    }

    /**
     * Converts trits (bytes array)to a tryte string based on {@link #NUMBER_OF_TRITS_IN_A_TRYTE}.<br>
     *     the inverse of {@link #trits(String, byte[], int)}.
     *
     * @param trits source trits array
     * @param offset starting position for trits
     * @param size amount of trits to convert
     * @return tryte string
     */
    public static String trytes(final byte[] trits, final int offset, final int size) {
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

    /**
     * Converts trits (bytes array)to a tryte string.<br>
     * @see #trytes(byte[], int, int)
     *
     * @param trits source trits array
     * @return tryte string
     */
    public static String trytes(final byte[] trits) {
        return trytes(trits, 0, trits.length);
    }

    /**
     * Creates a new trits array with the converted tryte string.<br>
     *     should be used only for testing as it allocates memory.
     * @see #allocatingTritsFromTrytes(String)
     * @see #trits(String, byte[], int)
     *
     * @param trytes source trytes string
     * @return a new trit array
     */
    public static byte[] allocatingTritsFromTrytes(String trytes) {
        byte[] trits = allocateTritsForTrytes(trytes.length());
        trits(trytes, trits, 0);
        return trits;
    }


    // Long <-> Trits

    /**
     * Parses the trits argument as a long value, based on <i>Balanced ternary</i> arithmetic
     * @param trits Array of trits to be parsed
     * @param srcPos Starting position in trits array
     * @param size Amount of trits to parse
     * @return the long value represented by the trits array
     */
    public static long longValue(final byte[] trits, final int srcPos, final int size) {

        long value = 0;
        for (int i = size; i-- > 0; ) {
            value = value * RADIX + trits[srcPos + i];
        }
        return value;
    }

    /**
     * fills a trit array with a representation of the {@code value} argument in <i>Balanced ternary</i> arithmetic

     * @param value a long value
     * @param destination destination trit array
     * @param offset starting position of destination array
     * @param size max amount of trits to fill
     */
    public static void copyTrits(final long value, final byte[] destination, final int offset, final int size) {
        long absoluteValue = value < 0 ? -value : value;
        for (int i = 0; i < size; i++) {

            int remainder = (int)(absoluteValue % RADIX);
            absoluteValue /= RADIX;
            if (remainder > MAX_TRIT_VALUE) {

                remainder = MIN_TRIT_VALUE;
                absoluteValue++;
            }
            destination[offset + i] = (byte) remainder;
        }

        if (value < 0) {
            for (int i = 0; i < size; i++) {
                destination[offset + i] = (byte) -destination[offset + i];
            }
        }
    }


    // Additional conversions

    /**
     * Converts an {@code ASCII string} into a {@code TryteString} representation.
     * <p>
     *     each ASCII character is converted to 2 Trytes.
     * </p>
     *
     * @param input ASCII string.
     * @return Trytes string
     */
    public static String asciiToTrytes(String input) {
        StringBuilder sb = new StringBuilder(80);
        for (int i = 0; i < input.length(); i++) {
            int asciiValue = input.charAt(i);
            // If not recognizable ASCII character, return null
            if (asciiValue > 255) {
                return null;
            }
            int firstValue = asciiValue % 27;
            int secondValue = (asciiValue - firstValue) / 27;
            sb.append(TRYTE_ALPHABET.charAt(firstValue));
            sb.append(TRYTE_ALPHABET.charAt(secondValue));
        }
        return sb.toString();
    }

    public static Pair<long[], long[]> longPair(byte[] trits) {
        long[] low = new long[trits.length];
        long[] hi = new long[trits.length];
        for(int i = 0; i< trits.length; i++) {
            low[i] = trits[i] != 1 ? HIGH_LONG_BITS: 0;
            hi[i] = trits[i] != -1 ? HIGH_LONG_BITS: 0;
        }
        return new Pair<>(low, hi);
    }

    public static byte[] trits(final Pair<long[], long[]> pair, final int bitIndex) {
        final int length;
        if(pair.low.length == pair.hi.length || pair.low.length < pair.hi.length) {
            length = pair.low.length;
        } else {
            length = pair.hi.length;
        }
        final byte[] trits = new byte[length];
        long low;
        long hi;
        int mask = 1 << bitIndex;
        for(int i = 0; i < length; i++) {
            low = pair.low[i] & mask;
            hi = pair.hi[i] & mask;
            if( hi == low ) {
                trits[i] = 0;
            } else if ( low == 0 ) {
                trits[i] = 1;
            } else if ( hi == 0 ) {
                trits[i] = -1;
            }
        }
        return trits;
    }

    public static byte[] trits(long[] low, long[] hi) {
        byte[] trits = new byte[low.length];
        for(int i = 0; i < trits.length; i++) {
            trits[i] = low[i] == 0 ? 1 : hi[i] == 0 ? (byte) -1 : (byte) 0;
        }
        return trits;
    }

    public static byte[] trits(byte[] low, byte[] hi) {
        byte[] trits = new byte[low.length];
        for(int i = 0; i < trits.length; i++) {
            trits[i] = low[i] == 0 ? 1 : hi[i] == 0 ? (byte) -1 : (byte) 0;
        }
        return trits;
    }
}
