package com.iota.iri.model;

import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Serializer;
import org.apache.commons.lang3.ArrayUtils;

import javax.naming.OperationNotSupportedException;
import java.util.List;

/**
 * Created by paul on 4/11/17.
 */
public class Milestone implements Persistable {
    public IntegerIndex index;
    public Hash hash;

    public byte[] bytes() {
        return ArrayUtils.addAll(index.bytes(), hash.bytes());
    }

    public void read(byte[] bytes) {
        if(bytes != null) {
            index = new IntegerIndex(Serializer.getInteger(bytes));
            hash = new Hash(bytes, Integer.BYTES, Hash.SIZE_IN_BYTES);
        }
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

    @Override
    public boolean isSplittable() {
        return false;
    }

    @Override
    public List<byte[]> splitBytes(int maxByteLength) throws OperationNotSupportedException {
        throw new OperationNotSupportedException();
    }
}
