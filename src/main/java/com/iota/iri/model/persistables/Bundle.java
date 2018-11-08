package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * Creates a persistable <tt>Bundle</tt> hash set. If a hash is provided, this hash is added
 * to the newly generated hash set.
 */
public class Bundle extends Hashes{

    /**
     * Constructor for persistable <tt>Bundle</tt> hash set.
     * @param hash the hash that the persistable set will be generated from
     */
    public Bundle(Hash hash) {
        set.add(hash);
    }

    /** Constructor for persistable <tt>Bundle</tt> hash set.*/
    public Bundle() {

    }
}
