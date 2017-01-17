package com.iota.iri.hash;

import com.iota.iri.utils.Converter;
import java.util.Random;
import static org.junit.Assert.*;

import org.junit.Test;

public class PearlDiverTest {

	final static int TRYTE_LENGTH = 2673;

	@Test
	public void testRandomTryteHash() {
		PearlDiver pearlDiver = new PearlDiver();
		Curl curl = new Curl();
		String hash;
		int[] hashTrits = new int[Curl.HASH_LENGTH],
				myTrits = new int[Curl.HASH_LENGTH];
		int i = 0,
		testCount = 20,
		minWeightMagnitude = 13,
		numCores = -1; // use n-1 cores
		
		do {
			String trytes = getRandomTrytes();
			myTrits = Converter.trits(trytes);
			pearlDiver.search(myTrits, minWeightMagnitude, numCores);
			curl.absorb(myTrits, 0, myTrits.length);
			curl.squeeze(hashTrits, 0, Curl.HASH_LENGTH);
			curl.reset();
			hash = Converter.trytes(hashTrits);
			boolean success = isAllNines(hash.substring(Curl.HASH_LENGTH/3-minWeightMagnitude/3));
			assertTrue("The hash should have n nines", success);
			if(!success) {
				System.out.println("Failed on iteration " + i);
				System.out.println("Hash: " + hash);
			}
		} while(i++ < testCount);

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
