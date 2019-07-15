package com.iota.iri.model;

/**
 * Creates a <tt>Tag</tt> hash identifier
 *
 * <p>
 *     Tags can be defined and used as a referencing hash for organizing and
 *     finding transactions. A unique tag can be included in multiple transactions
 *     and can then be used to identify these stored transactions in the database.
 * </p>
 */
public class TagHash extends AbstractHash {

    /**
     * Empty Constructor for a <tt>Tag</tt> hash identifier object. Creates a placeholder <tt>Tag</tt> hash identifier
     * object with no properties.
     */
    public TagHash() { }

    /**
     * Constructor for a <tt>Tag</tt> hash identifier using a source array and starting point
     *
     * @param tagBytes The trit or byte array source that the object will be generated from
     * @param offset The starting point in the array for the beginning of the Hash object
     * @param tagSizeInBytes The size of the Hash object that is to be created
     */    protected TagHash(byte[] tagBytes, int offset, int tagSizeInBytes) {
        super(tagBytes, offset, tagSizeInBytes);
    }
}
