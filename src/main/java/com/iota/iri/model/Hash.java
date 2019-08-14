package com.iota.iri.model;

import com.iota.iri.crypto.Curl;
import com.iota.iri.storage.Indexable;

/**
 * Represents an indexable hash object
 */
public interface Hash extends Indexable, HashId {

    /**
     * Creates a null transaction hash with from a byte array of length {@value Curl#HASH_LENGTH}.
     * This is used as a reference hash for the genesis transaction.
     */
    Hash NULL_HASH = HashFactory.TRANSACTION.create(new byte[Curl.HASH_LENGTH]);
    
    /**
     * The size of a hash stored in a byte[] when the data structure is trits
     */
	int SIZE_IN_TRITS = 243;
	
	 /**
     * The size of a hash stored in a byte[] when the data structure is bytes
     */
    int SIZE_IN_BYTES = 49;

    /**
     * The data of this hash in trits
     * @return the trits
     */
    public byte[] trits();
	
    /**
     * The amount of zeros this hash has on the end.
     * Defines the weightMagnitude for a transaction.
     * @return the trailing zeros
     */
	public int trailingZeros();
}
