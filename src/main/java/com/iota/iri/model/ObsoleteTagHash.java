package com.iota.iri.model;

/**
 * The <tt>Obsolete Tag</tt> hash identifier of a transaction
 *
 * <p>
 *     An Obsolete Tag is used for determining milestone indexes.
 *     If a milestone is issued from the coordinator address, first
 *     the signature is checked to confirm that the sender is indeed
 *     the coordinator, and then it will check the Obsolete Tag hash
 *     to determine the milestone's index.
 * </p>
 */
public class ObsoleteTagHash extends AbstractHash {

    /**
     * Constructor for a <tt>Obsolete Tag</tt> hash identifier using a source array and starting point
     *
     * @param tagBytes The trit or byte array source that the object will be generated from
     * @param offset The starting point in the array for the beginning of the Hash object
     * @param tagSizeInBytes The size of the Hash object that is to be created
     */
    protected ObsoleteTagHash(byte[] tagBytes, int offset, int tagSizeInBytes) {
        super(tagBytes, offset, tagSizeInBytes);
    }
}
