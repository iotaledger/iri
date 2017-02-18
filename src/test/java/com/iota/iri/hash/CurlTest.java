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


}