package com.iota.iri.model;

public class TagHash extends AbstractHash {

    public TagHash() { }

    protected TagHash(byte[] tagBytes, int offset, int tagSizeInBytes) {
        super(tagBytes, offset, tagSizeInBytes);
    }
}
