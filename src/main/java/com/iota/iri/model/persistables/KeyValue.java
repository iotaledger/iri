package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

public class KeyValue extends Hashes {

    public KeyValue(Hash hash) {
        set.add(hash);
    }

    // used by the persistence layer to instantiate the object
    public KeyValue() {

    }
}
