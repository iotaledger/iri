package com.iota.iri.model;

public class AddressHash extends AbstractHash {

    public AddressHash() { }

    protected AddressHash(byte[] bytes, int offset, int sizeInBytes) {
        super(bytes, offset, sizeInBytes);
    }
}
