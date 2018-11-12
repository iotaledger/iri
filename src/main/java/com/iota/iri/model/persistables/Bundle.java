package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * This is a collection of hash identifiers that can change the value of a <tt>Bundle</tt>.
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
