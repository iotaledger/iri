package com.iota.iri.model.persistables;

import java.util.BitSet;

import org.apache.commons.lang3.ArrayUtils;

import com.iota.iri.model.IntegerIndex;
import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Serializer;

public class CuckooBucket implements Persistable {

    public BitSet bucketBits;
    public IntegerIndex bucketIndex;
    
    @Override
    public byte[] bytes() {
        return ArrayUtils.addAll(bucketIndex.bytes(), bucketBits.toByteArray());
    }

    @Override
    public void read(byte[] bytes) {
        if(bytes != null) {
            bucketIndex = new IntegerIndex(Serializer.getInteger(bytes));
            
            bucketBits = new BitSet(bytes.length - 2);
            for (int i = 1; i < bytes.length; i++) {
                bucketBits.set(i-1, bytes[i]);
            }
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

}
