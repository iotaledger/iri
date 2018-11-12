package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * This is a collection of hash identifiers that change the value of an <tt>Address</tt>
 */
public class Address extends Hashes{

    /**
     * Instantiates an empty <tt>Address</tt> hash identifier collection
     */
    public Address(){}

    /**
     * Adds an <tt>Address</tt> hash identifier to the collection
     * @param hash The hash identifier that will be added to the collection
     */
    public Address(Hash hash) {
        set.add(hash);
    }
}
