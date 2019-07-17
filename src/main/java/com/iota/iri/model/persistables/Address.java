package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * This is a collection of {@link com.iota.iri.model.TransactionHash} identifiers indexed by their
 * {@link com.iota.iri.model.AddressHash} in the database.
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
