package com.iota.iri.model;

public class AddressHash extends AbstractHash {

    public AddressHash(byte[] bytes, int offset, int sizeInBytes) {
        super(bytes, offset, sizeInBytes);
    }

    public AddressHash(String string) {
        super(string);
    }
}
