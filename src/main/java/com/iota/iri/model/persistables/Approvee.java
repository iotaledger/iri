package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * Creates a persistable <tt>Approvee</tt> hash set. If a hash is provided, this hash is added
 * to the newly generated hash set.
 *
 * <p>
 *     An <tt>Approvee</tt> hash set is comprised of transaction hashes that reference
 *     a specific transaction. Approvee's are transactions that either directly or
 *     indirectly approve a transaction.
 * </p>
 */
public class Approvee extends Hashes{

    /**
     * Constructor for persistable <tt>Approvee</tt> hash set.
     * @param hash the hash that the persistable set will be generated from
     */
    public Approvee(Hash hash) {
        set.add(hash);
    }

    /**Constructor for persistable <tt>Approvee</tt> hash set.*/
    public Approvee() {

    }
}
