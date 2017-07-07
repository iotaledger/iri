package com.iota.iri.hash;

import com.iota.iri.controllers.TransactionViewModelTest;
import com.iota.iri.model.Hash;
import com.iota.iri.utils.Converter;
import java.util.Random;

import static org.junit.Assert.*;

import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class PearlDiverTest {

    private static final int TRYTE_LENGTH = 2673;
    private static final int MIN_WEIGHT_MAGNITUDE = 9;
    private static final int NUM_CORES = -1; // use n-1 cores

    private PearlDiver pearlDiver;
    private int[] hashTrits;

    @Before
    public void setUp() throws Exception {
        pearlDiver = new PearlDiver();
        hashTrits = new int[Curl.HASH_LENGTH];
    }

    @Test
    public void testRandomTryteHash() {
        String hash = getHashFor(getRandomTrytes());
        boolean success = isAllNines(hash.substring(Curl.HASH_LENGTH / 3 - MIN_WEIGHT_MAGNITUDE / 3));
        assertTrue("The hash should have n nines", success);
    }

    @Test
    public void testCancel() {
        pearlDiver.cancel();
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidMagnitude() {
        pearlDiver.search(new int[8019], -1, NUM_CORES);
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidTritsLength() {
        pearlDiver.search(new int[0], MIN_WEIGHT_MAGNITUDE, NUM_CORES);
    }

    @Test
    @Ignore("to test pearlDiver iteratively")
    public void testNoRandomFail() {
        for (int i = 0; i < 10000; i++) {
            int[] trits = TransactionViewModelTest.getRandomTransactionTrits();
            pearlDiver.search(trits, MIN_WEIGHT_MAGNITUDE, NUM_CORES);
            Hash hash = Hash.calculate(trits);
            for (int j = Hash.SIZE_IN_TRITS - 1; j > Hash.SIZE_IN_TRITS - MIN_WEIGHT_MAGNITUDE; j--) {
                assertEquals(hash.trits()[j], 0);
            }
            if (i % 100 == 0) {
                System.out.println(i + " successful hashes.");
            }
        }
    }

    private String getHashFor(String trytes) {
        Curl curl = new Curl();
        int[] myTrits = Converter.trits(trytes);
        pearlDiver.search(myTrits, MIN_WEIGHT_MAGNITUDE, NUM_CORES);
        curl.absorb(myTrits, 0, myTrits.length);
        curl.squeeze(hashTrits, 0, Curl.HASH_LENGTH);
        curl.reset();
        return Converter.trytes(hashTrits);
    }

    private String getRandomTrytes() {
        return new Random()
            .ints(TRYTE_LENGTH, 0, Converter.TRYTE_ALPHABET.length())
            .mapToObj(n -> String.valueOf(Converter.TRYTE_ALPHABET.charAt(n)))
            .collect(Collectors.joining());
    }

    private boolean isAllNines(String hash) {
        return hash.chars().allMatch(e -> e == '9');
    }
}
