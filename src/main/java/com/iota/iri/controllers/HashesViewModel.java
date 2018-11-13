package com.iota.iri.controllers;

import java.util.Set;

import com.iota.iri.model.Hash;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Tangle;

/**
 * Base implementation of a controller for hash model objects.
 */
public interface HashesViewModel {

    /**Stores the given tangle implementation to the controller*/
    boolean store(Tangle tangle) throws Exception;

    /**Variable that containst the size of the object*/
    int size();

    boolean addHash(Hash theHash);
    Indexable getIndex();
    Set<Hash> getHashes();
    void delete(Tangle tangle) throws Exception;

    HashesViewModel next(Tangle tangle) throws Exception;
}
