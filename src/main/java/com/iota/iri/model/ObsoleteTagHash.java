package com.iota.iri.model;

public class ObsoleteTagHash extends AbstractHash {

    public ObsoleteTagHash(byte[] tagBytes, int offset, int tagSizeInBytes) {
        super(tagBytes, offset, tagSizeInBytes);
    }

    public ObsoleteTagHash(String tag) {
        super(tag);
    }
}