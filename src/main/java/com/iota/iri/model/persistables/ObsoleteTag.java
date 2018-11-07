package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * Represents a persistable <tt>ObsoleteTag</tt> set
 *
 * <p>
 *     An Obsolete Tag is used for determining milestone indexes.
 *     If a milestone is issued from the coordinator address, first
 *     the signature is checked to confirm that the sender is indeed
 *     the coordinator, and then it will check the Obsolete Tag hash
 *     to determine the new index.
 * </p>
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
