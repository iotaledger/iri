package com.iota.iri.model;

public class BundleHash extends AbstractHash {

    public BundleHash(byte[] bytes, int offset, int sizeInBytes) {
        super(bytes, offset, sizeInBytes);
    }

    public BundleHash(String bundle) {
        super(bundle);
    }
}
