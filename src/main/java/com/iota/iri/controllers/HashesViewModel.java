package com.iota.iri.controllers;

import java.util.Set;

import com.iota.iri.model.Hash;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Tangle;

/**
 * Base implementation of a controller interface for hash model objects.
 */
public interface HashesViewModel {

    /**Stores the given tangle implementation to the controller*/
    boolean store(Tangle tangle) throws Exception;

    /**Returns the size of the model object*/
    int size();

    boolean addHash(Hash theHash);

    /**Returns the index of the model reference*/
    Indexable getIndex();

    /**Returns the hash identifiers stored in the model set*/
    Set<Hash> getHashes();

    /**
     * Removes a referenced object from the database.
     * @throws Exception If the object does not exist or fails to be removed.
     */
    void delete(Tangle tangle) throws Exception;

    /**
     * Returns the next indexed persistable in the database.
     * @throws Exception If there is no next available persistable
     */
    HashesViewModel next(Tangle tangle) throws Exception;
}
