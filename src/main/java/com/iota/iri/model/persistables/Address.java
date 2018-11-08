package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * Creates a persistable <tt>Address</tt> hash set. If a hash is provided, this hash is added
 * to the newly generated hash set.
 */
public class Address extends Hashes{

    /**Constructor for persistable <tt>Address</tt> hash set*/
    public Address(){}

    /**
     * Constructor for persistable <tt>Address</tt> hash set from a provided hash
     * @param hash the hash that the persistable set will be generated from
     */
    public Address(Hash hash) {
        set.add(hash);
    }
}
