package com.iota.iri.controllers;

import java.util.Set;

import com.iota.iri.model.Hash;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Tangle;

/**
 * Base implementation of a controller interface for sets of {@link com.iota.iri.model.persistables.Hashes}.
 */
public interface HashesViewModel {

    /**Store the {@link com.iota.iri.model.persistables.Hashes} set and {@link Hash} reference to the database*/
    boolean store(Tangle tangle) throws Exception;

    /**Returns the size of the {@link com.iota.iri.model.persistables.Hashes} set*/
    int size();

    /**Add a hash object to the controllers referenced {@link com.iota.iri.model.persistables.Hashes} set*/
    boolean addHash(Hash theHash);

    /**Returns the {@link Hash} identifier of the {@link com.iota.iri.model.persistables.Hashes} set*/
    Indexable getIndex();

    /**Returns the {@link Hash} identifiers stored in the {@link com.iota.iri.model.persistables.Hashes} set*/
    Set<Hash> getHashes();

    /**
     * Removes a referenced {@link com.iota.iri.model.persistables.Hashes} set from the database
     *
     * @throws Exception If the {@link com.iota.iri.model.persistables.Hashes} set does not exist or fails to be removed
     */
    void delete(Tangle tangle) throws Exception;

    /**
     * Returns the next indexed {@link com.iota.iri.model.persistables.Hashes} set in the database.
     * @throws Exception If there is no next available {@link com.iota.iri.model.persistables.Hashes} set
     */
    HashesViewModel next(Tangle tangle) throws Exception;
}
