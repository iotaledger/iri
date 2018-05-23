package com.iota.iri.service.tipselection;

import com.iota.iri.model.Hash;

import java.util.Optional;

public interface TailFinder {

    Optional<Hash> findTail(Hash hash) throws Exception;

}
