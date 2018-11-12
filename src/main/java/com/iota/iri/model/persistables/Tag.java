package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * This is a collection of hash identifiers that can change the value of a <tt>Tag</tt>.
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
