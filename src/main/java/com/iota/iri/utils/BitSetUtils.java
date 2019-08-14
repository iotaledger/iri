package com.iota.iri.utils;

import java.util.BitSet;

/**
 * This class offers utility methods to transform BitSets into different data types.
 */
public class BitSetUtils {
    /**
     * This method converts a byte array to a {@link BitSet} of the given size ({@code sizeOfBitSet}) by copying the
     * bits of every byte into the {@link BitSet} in reverse order (starting with the given {@code startOffset}.
     *
     * It first checks if the byte array is big enough to provide enough bits for the provided parameters and then
     * starts the copying process.
     *
     * @param byteArray byte array that shall be converted
     * @param startOffset the amount of bytes to skip at the start
     * @param sizeOfBitSet the desired amount of bits in the resulting {@link BitSet}
     * @return the {@link BitSet} containing the extracted bytes
     */
    public static BitSet convertByteArrayToBitSet(byte[] byteArray, int startOffset, int sizeOfBitSet) {
        if((byteArray.length - startOffset) * 8 < sizeOfBitSet) {
            throw new IllegalArgumentException("the byte[] is too small to create a BitSet of length " + sizeOfBitSet);
        }

        BitSet result = new BitSet(sizeOfBitSet);

        int bitMask = 128;
        for(int i = 0; i < sizeOfBitSet; i++) {
            // insert the bits in reverse order
            result.set(i, (byteArray[i / 8 + startOffset] & bitMask) != 0);

            bitMask = bitMask / 2;

            if(bitMask == 0) {
                bitMask = 128;
            }
        }

        return result;
    }

    /**
     * Does the same as {@link #convertByteArrayToBitSet(byte[], int, int)} but defaults to copy all remaining bytes
     * following the {@code startOffset}.
     *
     * @param byteArray byte array that shall be converted
     * @param startOffset the amount of bytes to skip at the start
     * @return the {@link BitSet} containing the extracted bytes
     */
    public static BitSet convertByteArrayToBitSet(byte[] byteArray, int startOffset) {
        return convertByteArrayToBitSet(byteArray, startOffset, (byteArray.length - startOffset) * 8);
    }

    /**
     * Does the same as {@link #convertByteArrayToBitSet(byte[], int, int)} but defaults to a {@code startOffset} of 0
     * and the full length for {@code sizeOfBitSet} resulting in converting the full byte array.
     *
     * @param byteArray byte array that shall be converted
     * @return the {@link BitSet} containing the bytes of the byte array
     */
    public static BitSet convertByteArrayToBitSet(byte[] byteArray) {
        return convertByteArrayToBitSet(byteArray, 0);
    }

    /**
     * Converts a {@link BitSet} into a byte array by copying the bits in groups of 8 into the resulting bytes of the
     * array.
     *
     * It first calculates the size of the resulting array and then iterates over the bits of the {@link BitSet} to
     * write them into the correct index of the byte array. We write the bits in reverse order, shifting them to the
     * left before every step.
     *
     * If the {@link BitSet} is not big enough to fill up the last byte, we fill the remaining bits with zeros by
     * shifting the previously written bits to the left accordingly.
     *
     * @param bitSet the {@link BitSet} that shall be converted.
     * @return the byte array containing the bits of the {@link BitSet} in groups of 8
     */
    public static byte[] convertBitSetToByteArray(BitSet bitSet) {
        int lengthOfBitSet = bitSet.length();
        int lengthOfArray = (int) Math.ceil(lengthOfBitSet / 8.0);

        byte[] result = new byte[lengthOfArray];

        for(int i = 0; i < lengthOfBitSet; i++) {
            // for every new index -> start with a 1 so the shifting keeps track of the position we are on (gets shifted
            // out when we arrive at the last bit of the current byte)
            if(i % 8 == 0) {
                result[i / 8] = 1;
            }

            // shift the existing bits to the left to make space for the bit that gets written now
            result[i / 8] <<= 1;

            // write the current bit
            result[i / 8] ^= bitSet.get(i) ? 1 : 0;

            // if we are at the last bit of the BitSet -> shift the missing bytes to "fill up" the remaining space (in
            // case the BitSet was not long enough to fill up a full byte)
            if(i == (lengthOfBitSet - 1)) {
                result[i / 8] <<= (8 - (i % 8) - 1);
            }
        }

        return result;
    }
}
