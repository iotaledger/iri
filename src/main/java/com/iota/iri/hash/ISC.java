package com.iota.iri.hash;

import com.iota.iri.utils.Converter;

import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

/**
 * Created by paul on 2/13/17.
 */
public class ISC {

    /*
                B
          | -   0   +
        __|____________
        - | 1   -1  0
      A 0 | -1  0   1
        + | 0   1   -1
     */
    private static IntFunction<Integer> sum (int a) {
        return (b) -> {
            int s = a + b;
            return s == 2 ? -1 : s == -2 ? 1 : s;
        };
    }

    /*
        Vernam Cipher for trinary logic
        plain (+) key = cipher
     */
    public static Function<String, String> encrypt(String key) {
        final int[] keyTrits = Converter.trits(key);
        return (plainText) -> {
            assert key.length() == plainText.length();
            final int[] plainTrits = Converter.trits(plainText);
            return Converter.trytes(
                    IntStream.range(0, plainTrits.length).parallel()
                            .map(i -> sum(plainTrits[i]).apply(keyTrits[i]))
                            .toArray()
            );
        };
    }
    /*
        cipher (+) !key = plain
     */
    public static Function<String, String> decrypt(String key) {
        final int[] keyTrits = Converter.trits(key);
        return (cipherText) -> {
            assert key.length() >= cipherText.length();
            final int[] cipherTrits = Converter.trits(cipherText);
            return Converter.trytes(
                    IntStream.range(0, cipherTrits.length).parallel()
                            .map(i -> sum(cipherTrits[i]).apply( - keyTrits[i]))
                            .toArray()
            );
        };
    }
}
