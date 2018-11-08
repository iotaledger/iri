package com.iota.iri.model;

/**
 * Creates an Address hash object
 *
 * <p>
 *     An Address hash can represent the sending or receiving party of a transaction. The Address
 *     hash can also be used to find specific associated transactions within the DB of a node using
 *     the API.
 * </p>
 */

public class AddressHash extends AbstractHash {

    /**
     * Constructor for an Address hash object using a source array and starting point
     *
     * @param bytes The trit or byte array source that the object will be generated from
     * @param offset The starting point in the array for the beginning of the Address Hash object
     * @param sizeInBytes The size of the Address Hash object that is to be created
     */
    protected AddressHash(byte[] bytes, int offset, int sizeInBytes) {
        super(bytes, offset, sizeInBytes);
    }
}
