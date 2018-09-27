package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

/**
 * Created by paul on 5/15/17.
 */
public class Bundle extends Hashes{
    public Bundle(Hash hash) {
        set.add(hash);
    }

    public Bundle() {

    }
}
