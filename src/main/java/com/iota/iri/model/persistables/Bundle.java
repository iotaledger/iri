package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * This is a collection of {@link com.iota.iri.model.TransactionHash} identifiers indexed by their
 * {@link com.iota.iri.model.BundleHash} in the database.
 */
public class Bundle extends Hashes{

    /**
     * Adds a <tt>Bundle</tt> hash identifier to the collection.
     * @param hash The hash identifier that will be added to the collection.
     */
    public Bundle(Hash hash) {
        set.add(hash);
    }

    /**Instantiates a collection of <tt>Bundle</tt> hash identifiers.*/
    public Bundle() {

    }
}
