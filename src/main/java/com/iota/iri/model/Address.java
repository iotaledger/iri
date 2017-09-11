package com.iota.iri.model;

/**
 * Created by paul on 5/15/17.
 */
public class Address extends Hashes{
    public Address(){}
    public Address(Hash hash) {
        set.add(hash);
    }
}
