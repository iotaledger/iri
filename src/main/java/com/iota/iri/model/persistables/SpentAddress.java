package com.iota.iri.model.persistables;

import com.iota.iri.storage.Persistable;

public class SpentAddress implements Persistable {
    private boolean exists = false;

    @Override
    public byte[] bytes() {
        return new byte[0];
    }

    @Override
    public void read(byte[] bytes) {
        exists = bytes != null;
    }

    @Override
    public byte[] metadata() {
        return new byte[0];
    }

    @Override
    public void readMetadata(byte[] bytes) {
    }

    @Override
    public boolean merge() {
        return false;
    }

    public boolean exists() {
        return exists;
    }
}
