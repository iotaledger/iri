package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * This is a collection of hash identifiers that can change the values of an <tt>Approvee</tt>
 *
 * <p>
 *     An <tt>Approvee</tt> hash set is comprised of transaction hashes that reference
 *     specific transactions. Approvee's are transactions that either directly or
 *     indirectly approve a transaction.
 * </p>
 */
public class Approvee extends Hashes{

    /**
     * Adds an <tt>Approvee</tt> identifier to the collection.
     * @param hash The hash identifier that will be added to the collection
     */
    public Approvee(Hash hash) {
        set.add(hash);
    }

    /**Instantiates a collection of <tt>Approvee</tt> hash identifiers.*/
    public Approvee() {

    }
}
