package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * Represents a persistable <tt>ObsoleteTag</tt> set
 */

public class ObsoleteTag extends Tag {

    /**
     * Constructor for persistable <tt>ObsoleteTag</tt> set
     * @param hash
     */
    public ObsoleteTag(Hash hash) {
        super(hash);
    }

    // used by the persistence layer to instantiate the object
    public ObsoleteTag() {

    }
}
