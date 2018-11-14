package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * This is a collection of {@link com.iota.iri.model.TransactionHash} identifiers indexed by a given
 * <tt>Aprovee</tt> {@link com.iota.iri.model.TransactionHash} in the database.
 *
 * <p>
 *     An <tt>Approvee</tt> hash set is comprised of transaction hashes that reference
 *     a specific transaction. Approvee's are transactions that directly approve a given
 *     transaction.
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

    /**Instantiates an empty <tt>Approvee</tt> hash identifier collection.*/
    public Approvee() {

    }
}
