package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * This is a collection of hash identifiers that can change the values of an <tt>Obsolete Tag</tt>.
 * This is part of the bundle essence, and for normal transactions, a {@link Tag} collection should be
 * used instead.
 */

public class ObsoleteTag extends Tag {

    /**
     * Adds an <tt>Obsolete Tag</tt> hash identifier to the collection
     * @param hash The hash identifier that will be added to the collection
     */
    public ObsoleteTag(Hash hash) {
        super(hash);
    }

    // used by the persistence layer to instantiate the object
    public ObsoleteTag() {

    }
}
