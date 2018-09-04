package com.iota.iri.model;

import com.iota.iri.hash.Sponge;
import com.iota.iri.hash.SpongeFactory;
import com.iota.iri.utils.Converter;

public class TransactionHash extends AbstractHash {
	public TransactionHash(String source) {
	    super(source);
	}

	public TransactionHash(byte[] source, int offset, int sourceSize) {
		super(source, offset, sourceSize);
	}
	
    public static Hash calculate(SpongeFactory.Mode mode, byte[] trits) {
        return calculate(trits, 0, trits.length, SpongeFactory.create(mode));
    }

    public static Hash calculate(byte[] bytes, int tritsLength, final Sponge curl) {
        byte[] trits = new byte[tritsLength];
        Converter.getTrits(bytes, trits);
        return calculate(trits, 0, tritsLength, curl);
    }

    public static Hash calculate(final byte[] tritsToCalculate, int offset, int length, final Sponge curl) {
        byte[] hashTrits = new byte[SIZE_IN_TRITS];
        curl.reset();
        curl.absorb(tritsToCalculate, offset, length);
        curl.squeeze(hashTrits, 0, SIZE_IN_TRITS);
        return HashFactory.TRANSACTION.create(hashTrits, 0, SIZE_IN_TRITS);
    }
}
