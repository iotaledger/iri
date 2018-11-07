package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * Creates a persistable <tt>Bundle</tt> hash set. If a hash is provided, this hash is added
 * to the newly generated hash set.
 *
 * <p>
 *     Bundles are collections of transactions that can be attached to the tangle at once.
 *     When transactions are placed in a bundle, they have the Bundle hash added to them.
 *     This bundle hash can be used to find these transactions and confirm that they were
 *     a part of the same bundle. If a bundle is deemed invalid, all transactions contained
 *     will also be deemed invalid.
 * </p>
 */
public class Bundle extends Hashes{

    /**
     * Constructor for persistable <tt>Bundle</tt> hash set.
     * @param hash the hash that the persistable set will be generated from
     */
    public Bundle(Hash hash) {
        set.add(hash);
    }

    /** Constructor for persistable <tt>Bundle</tt> hash set.*/
    public Bundle() {

    }
}
