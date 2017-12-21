package com.iota.iri.utils;

import java.util.Arrays;

public class IotaUtils {
    public static boolean containsArray(Iterable<byte[]> collectionOfArrays, byte[] array){
        if (collectionOfArrays == null)
            return false;

        for (byte[] arr: collectionOfArrays) {
            if (Arrays.equals(arr, array)) {
                return true;
            }
        }
        return false;
    }
}
