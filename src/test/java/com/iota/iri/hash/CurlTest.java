package com.iota.iri.hash;

import com.iota.iri.utils.Converter;
import org.junit.Test;

import java.util.Arrays;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

/**
 * Created by paul on 2/17/17.
 */
public class CurlTest {
    @Test
    public void staticSponge() throws Exception {
        Curl curl = new Curl();
        final String curlStr = "MYCURLSARETHEBEST9CURLSWHODONTUSEMYCURLARESADALLCURLSSHOULDBEZEROLENGTHORGREATER9";
        final int[] curlTrits, staticTrits, hashTrits;
        final Tuple[] curlState, staticState, scratchpad;

        curlTrits = Converter.trits(curlStr);
        curlState = Converter.tuple(curlTrits);
        staticState = Curl.absorb(curlState, 0, curlTrits.length)
                .andThen(Curl.squeeze(
                        Arrays.stream(new Tuple[Curl.HASH_LENGTH])
                                .parallel()
                                .map(i -> new Tuple(0))
                                .toArray(Tuple[]::new), 0, Curl.HASH_LENGTH)
                )
                .apply(Curl.state());
        staticTrits = Converter.trits(staticState);

        hashTrits = new int[Curl.HASH_LENGTH];
        curl.absorb(curlTrits, 0, curlTrits.length);
        curl.squeeze(hashTrits, 0, Curl.HASH_LENGTH);

        assertArrayEquals(hashTrits, staticTrits);
    }

    @Test
    public void spongeSpeed() throws Exception {
        Curl curl = new Curl();
        final String curlStr = "MYCURLSARETHEBEST9CURLSWHODONTUSEMYCURLARESADALLCURLSSHOULDBEZEROLENGTHORGREATER9";
        final int[] curlTrits;
        Tuple[] curlState, staticState;
        long startTime, r = 0;
        long diff1, diff2, diff3;

        curlTrits = Converter.trits(curlStr);
        curlState = Converter.tuple(curlTrits);

        startTime = System.nanoTime();
        int max = (int)2e4;
        do {
            Curl.absorb(curlState, 0, curlTrits.length)
                    .andThen(Curl.squeeze(
                            Arrays.stream(new Tuple[Curl.HASH_LENGTH])
                                    .parallel()
                                    .map(i -> new Tuple(0))
                                    .toArray(Tuple[]::new), 0, Curl.HASH_LENGTH)
                    )
                    .apply(Curl.state());
            for (int i = 0; i < 32; i++) {
                curl.absorb(curlTrits, 0, curlTrits.length);
                curl.squeeze(new int[Curl.HASH_LENGTH], 0, Curl.HASH_LENGTH);
                curl.reset();
            }
        }
        while(r++ < max);
        System.out.println((System.nanoTime() - startTime) / 1e9);
        startTime = System.nanoTime();
        Curl.absorb(curlState, 0, curlTrits.length)
                .andThen(Curl.squeeze(
                        Arrays.stream(new Tuple[Curl.HASH_LENGTH])
                                .parallel()
                                .map(i -> new Tuple(0))
                                .toArray(Tuple[]::new), 0, Curl.HASH_LENGTH)
                )
                .apply(Curl.state());
        diff1 = System.nanoTime() - startTime;

        startTime = System.nanoTime();
        for(int i = 0; i < 32; i++) {
            curl.absorb(curlTrits, 0, curlTrits.length);
            curl.squeeze(new int[Curl.HASH_LENGTH], 0, Curl.HASH_LENGTH);
            curl.reset();
        }
        diff2 = System.nanoTime() - startTime;
        startTime = System.nanoTime();
        IntStream.range(0,32).parallel().forEach(i -> {
            curl.absorb(curlTrits, 0, curlTrits.length);
            curl.squeeze(new int[Curl.HASH_LENGTH], 0, Curl.HASH_LENGTH);
            curl.reset();
        });
        diff3 = System.nanoTime() - startTime;
        System.out.println(diff1 / 1e9);
        System.out.println(diff3 / 1e9);
        System.out.println(diff2 / 1e9);

    }

}