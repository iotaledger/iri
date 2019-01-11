package com.iota.iri.controllers;

import java.util.Set;

import com.iota.iri.model.Hash;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Tangle;

/**
 * Base implementation of a controller interface for sets of {@link com.iota.iri.model.persistables.Hashes}.
 */
public interface HashesViewModel {

    /**
     * Store the {@link com.iota.iri.model.persistables.Hashes} set and {@link Hash} reference to the database
     *
     * @param tangle The tangle reference for the database
     * @return True if the object was saved correctly, False if not
     * @throws Exception Thrown if the {@link com.iota.iri.model.persistables.Hashes} set or index {@link Hash} are null
     */
    boolean store(Tangle tangle) throws Exception;

    /**
     * @return The size of the {@link com.iota.iri.model.persistables.Hashes} set referenced by the controller
     */
    int size();

    /**
     * Add a hash object to the controllers referenced {@link com.iota.iri.model.persistables.Hashes} set
     *
     * @param theHash The {@link Hash} identifier to be added to the set
     * @return True if the {@link com.iota.iri.model.persistables.Hashes} set is added correctly, False if not
     */
    boolean addHash(Hash theHash);

    /**
     * @return The {@link Hash} identifier of the {@link com.iota.iri.model.persistables.Hashes} set
     */
    Indexable getIndex();

    /**
     * @return The {@link com.iota.iri.model.persistables.Hashes} set referenced by the controller
     */
    Set<Hash> getHashes();

    /**
     * Deletes a referenced {@link com.iota.iri.model.persistables.Hashes} set from the database
     *
     * @param tangle The tangle reference for the database
     * @throws Exception If the {@link com.iota.iri.model.persistables.Hashes} set does not exist or fails to be removed
     */
    void delete(Tangle tangle) throws Exception;

    /**
     * Fetches the next indexed persistable {@link com.iota.iri.model.persistables.Hashes} set from the database and
     * generates a new {@link HashesViewModel} from it. If no {@link com.iota.iri.model.persistables.Hashes} sets exist
     * in the database, it will return null.
     *
     * @param tangle The tangle reference for the database
     * @return The new {@link HashesViewModel}
     * @throws Exception If the database fails to return a next {@link com.iota.iri.model.persistables.Hashes} set
     */
    HashesViewModel next(Tangle tangle) throws Exception;
}
