package com.iota.iri.model;

/**
 * The public <tt>Address</tt> hash identifier of a transaction
 *
 * <p>
 *     An <tt>Address</tt> hash can represent the sending or receiving party of a transaction. The Address
 *     hash can also be used to find specific associated transactions within the DB of a node using
 *     the API.
 * </p>
 */

public class AddressHash extends AbstractHash {

    /**
     * Empty Constructor for an <tt>Address</tt> hash identifier object. Creates a placeholder <tt>Address</tt> hash
     * identifier object with no properties.
     */
    public AddressHash() { }

    /**
     * Constructor for an <tt>Address</tt> hash identifier using a source array and starting point
     *
     * @param bytes The trit or byte array source that the object will be generated from
     * @param offset The starting point in the array for the beginning of the Address Hash object
     * @param sizeInBytes The size of the Address Hash object that is to be created
     */
    protected AddressHash(byte[] bytes, int offset, int sizeInBytes) {
        super(bytes, offset, sizeInBytes);
    }
}
