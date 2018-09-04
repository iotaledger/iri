package com.iota.iri.model;

public class TagHash extends AbstractHash {

    public TagHash(byte[] tagBytes, int offset, int tagSizeInBytes) {
        super(tagBytes, offset, tagSizeInBytes);
    }

    public TagHash(String tag) {
        super(tag);
    }
}
