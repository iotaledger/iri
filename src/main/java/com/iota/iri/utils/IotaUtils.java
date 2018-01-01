package com.iota.iri.utils;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class IotaUtils {
    public static boolean containsArray(Iterable<byte[]> collectionOfArrays, byte[] array) {
        if (collectionOfArrays == null)
            return false;

        for (byte[] arr : collectionOfArrays) {
            if (Arrays.equals(arr, array)) {
                return true;
            }
        }
        return false;
    }


    /**
     * @param array byte array
     * @return the array above with the integer represented by the last 4 bytes incremented by 1
     * @throws Exception in case of overflow
     */
    public static byte[] incrementArrayIntegerSuffix(byte[] array) throws Exception {
        return advanceByteArray(array, 3, array.length - 1);
    }

    private static byte[] advanceByteArray(byte[] array, int remainingCarryPasses, int currentIndexToAdvance) throws Exception {
        if (array[currentIndexToAdvance] == Byte.MAX_VALUE) {
            if (remainingCarryPasses == 0) {
                throw new Exception("Can't advance the byte array without superseding allowed carry");
            }
            if (currentIndexToAdvance == 0) {
                throw new Exception("Can't advance the byte array without overflowing");
            }
            advanceByteArray(array, --remainingCarryPasses, --currentIndexToAdvance);
        }
        array[currentIndexToAdvance]++;
        return array;
    }

}
