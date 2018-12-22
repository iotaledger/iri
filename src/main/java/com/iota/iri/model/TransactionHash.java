package com.iota.iri.model;

import com.iota.iri.crypto.Sponge;
import com.iota.iri.crypto.SpongeFactory;
import com.iota.iri.utils.Converter;

public class TransactionHash extends AbstractHash {

	protected TransactionHash(byte[] source, int offset, int sourceSize) {
		super(source, offset, sourceSize);
	}
	
    /**
     * Calculates a transaction hash from an array of bytes. Uses the entire trits array
     * @param mode The mode we absorb the trits with
     * @param trits array of trits we calculate the hash with
     * @return The {@link TransactionHash}
     */
    public static TransactionHash calculate(SpongeFactory.Mode mode, byte[] trits) {
        return calculate(trits, 0, trits.length, SpongeFactory.create(mode));
    }

    /**
     * Calculates a transaction hash from an array of bytes
     * @param bytes The bytes that contain this transactionHash 
     * @param tritsLength The length of trits the bytes represent
     * @param sponge The way we absorb the trits with
     * @return The {@link TransactionHash}
     */
    public static TransactionHash calculate(byte[] bytes, int tritsLength, Sponge sponge) {
        byte[] trits = new byte[tritsLength];
        Converter.getTrits(bytes, trits);
        return calculate(trits, 0, tritsLength, sponge);
    }

    /**
     * Calculates a transaction hash from an array of bytes
     * @param tritsToCalculate array of trits we calculate the hash with
     * @param offset The position we start reading from inside the tritsToCalculate array
     * @param length The length of trits the bytes represent
     * @param sponge The way we absorb the trits with
     * @return The {@link TransactionHash}
     */
    public static TransactionHash calculate(byte[] tritsToCalculate, int offset, int length, Sponge sponge) {
        byte[] hashTrits = new byte[SIZE_IN_TRITS];
        sponge.reset();
        sponge.absorb(tritsToCalculate, offset, length);
        sponge.squeeze(hashTrits, 0, SIZE_IN_TRITS);
        return (TransactionHash) HashFactory.TRANSACTION.create(hashTrits, 0, SIZE_IN_TRITS);
    }
}
