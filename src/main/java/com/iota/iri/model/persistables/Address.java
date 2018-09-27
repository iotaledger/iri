package com.iota.iri.model.persistables;

import com.iota.iri.model.Hash;

public class Address extends Hashes{
    public Address(){}
    public Address(Hash hash) {
        set.add(hash);
    }
}
