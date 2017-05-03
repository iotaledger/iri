package com.iota.iri.hash;

import com.iota.iri.controllers.TransactionViewModelTest;
import com.iota.iri.model.Hash;
import com.iota.iri.utils.Converter;
import java.util.Random;
import static org.junit.Assert.*;

import org.junit.Test;

public class PearlDiverTest {

	private final static int TRYTE_LENGTH = 2673;

	@Test
	public void testRandomTryteHash() {
		PearlDiver pearlDiver = new PearlDiver();
		Curl curl = new Curl();
		String hash;
		int[] hashTrits = new int[Curl.HASH_LENGTH],
				myTrits;
		int i = 0,
		testCount = 20,
		minWeightMagnitude = 9,
		numCores = -1; // use n-1 cores
		
        String trytes = getRandomTrytes();
        myTrits = Converter.trits(trytes);
        pearlDiver.search(myTrits, minWeightMagnitude, numCores);
        curl.absorb(myTrits, 0, myTrits.length);
        curl.squeeze(hashTrits, 0, Curl.HASH_LENGTH);
        curl.reset();
        hash = Converter.trytes(hashTrits);
        boolean success = isAllNines(hash.substring(Curl.HASH_LENGTH/3-minWeightMagnitude/3));
        assertTrue("The hash should have n nines", success);

	}

	// Remove below comment to test pearlDiver iteratively
    //@Test
    public void testNoRandomFail() {
        PearlDiver pearlDiver = new PearlDiver();

        int[] trits;
        Hash hash;
        int minWeightMagnitude = 9, numCores = -1; // use n-1 cores
        for(int i = 0; i++ < 10000;) {
            trits = TransactionViewModelTest.getRandomTransactionTrits();
            pearlDiver.search(trits, minWeightMagnitude, numCores);
            hash = Hash.calculate(trits);
            for(int j = Hash.SIZE_IN_TRITS; j --> Hash.SIZE_IN_TRITS - minWeightMagnitude;) {
                assertEquals(hash.trits()[j], 0);
            }
            if(i % 100 == 0) {
                System.out.println(i + " successful hashes.");
            }
        }
    }

	private String getRandomTrytes() {
		String trytes = "";
		Random rand = new Random();
		int i, r;
		for(i = 0; i < TRYTE_LENGTH; i++) {
			r = rand.nextInt(27);
			trytes += Converter.TRYTE_ALPHABET.charAt(r);
		}
		return trytes;
	}
	
	private boolean isAllNines(String hash) {
		int i;
		for(i = 0; i < hash.length(); i++) {
			if(hash.charAt(i) != '9') {
				return false;
			}
		}
		return true;
	}
}
