package com.iota.iri.model.persistables;

import com.iota.iri.storage.Persistable;

import javax.naming.OperationNotSupportedException;

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
    public boolean canMerge() {
        return false;
    }


    @Override
    public Persistable mergeInto(Persistable source)  throws OperationNotSupportedException {
        throw new OperationNotSupportedException("This object is not mergeable");
    }


    @Override
    public boolean exists() {
        return exists;
    }
}
