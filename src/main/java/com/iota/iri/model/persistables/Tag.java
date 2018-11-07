package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * Represents a persistable <tt>Tag</tt> hash set.
 *
 * <p>
 *     Tags can be defined and used as a referencing hash for organizing and
 *     finding transactions. A unique tag can be included in multiple transactions
 *     and can then be used to identify these stored transactions in the database.
 * </p>
 */
public class Tag extends Hashes {

    /**
     * Constructor for persistable <tt>Tag</tt> hash set.
     * @param hash the hash that the persistable set will be generated from
     */
    public Tag(Hash hash) {
        set.add(hash);
    }

    /**
     * Constructor for persistable <tt>Bundle</tt> hash set.
     */
    public Tag() {

    }
}
