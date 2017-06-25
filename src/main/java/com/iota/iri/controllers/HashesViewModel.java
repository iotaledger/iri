package com.iota.iri.controllers;

import com.iota.iri.model.Hash;
import com.iota.iri.storage.Tangle;

import java.util.Set;

/**
 * Created by paul on 5/6/17.
 */
public interface HashesViewModel {
    int size();
    Set<Hash> getHashes();
    void delete(Tangle tangle) throws Exception;

    HashesViewModel next(Tangle tangle) throws Exception;
}
