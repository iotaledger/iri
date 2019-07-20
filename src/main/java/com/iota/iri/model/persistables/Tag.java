package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * This is a collection of {@link com.iota.iri.model.TransactionHash} identifiers indexed by their
 * {@link com.iota.iri.model.TagHash} in the data base.
 */
public class Tag extends Hashes {

    /**
     * Adds a <tt>Tag</tt> hash identifier to the collection
     * @param hash The hash identifier that will be added to the collection
     */
    public Tag(Hash hash) {
        set.add(hash);
    }

    /**Instantiates an empty <tt>Tag</tt> hash identifier collection*/
    public Tag() {

    }
}
