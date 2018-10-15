package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * Created by paul on 5/15/17.
 */
public class Tag extends Hashes {
    public Tag(Hash hash) {
        set.add(hash);
    }

    public Tag() {

    }
}
