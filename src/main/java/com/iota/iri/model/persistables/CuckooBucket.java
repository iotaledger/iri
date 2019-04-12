package com.iota.iri.model.persistables;

import java.util.BitSet;

import org.apache.commons.lang3.ArrayUtils;

import com.iota.iri.model.IntegerIndex;
import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Serializer;

/**
 * Persistable to manage the data we get as a result of pruning a milestone and its transactions
 */
public class CuckooBucket implements Persistable {

    /**
     * The filter number it belonged to in previous cycles
     */
    public IntegerIndex bucketId;
    
    /**
     * 
     */
    public BitSet bucketBits;
    public IntegerIndex bucketIndex;
    
    /**
     * 
     * {@inheritDoc}
     */
    @Override
    public byte[] bytes() {
        byte[] index = bucketIndex.bytes();
        byte[] num = bucketId.bytes();
        return ArrayUtils.addAll(ArrayUtils.addAll(num, index), bucketBits.toByteArray());
    }

    /**
     * Reads a CuckooBucket from the provided bytes.
     * First 4 bytes are the bucket id, second 4 are the index inside that bucket
     * Rest of the bytes is the bucket data
     * 
     * {@inheritDoc}
     */
    @Override
    public void read(byte[] bytes) {
        if(bytes != null) {
            bucketId = new IntegerIndex(Serializer.getInteger(bytes, 0));
            bucketIndex = new IntegerIndex(Serializer.getInteger(bytes, 4));
            
            short start = 8;
            bucketBits = new BitSet(bytes.length - start);
            for (int i = start; i < bytes.length; i++) {
                bucketBits.set(i-start, bytes[i]);
            }
        }
    }

    /**
     * 
     * {@inheritDoc}
     */
    @Override
    public byte[] metadata() {
        return new byte[0];
    }

    /**
     * 
     * {@inheritDoc}
     */
    @Override
    public void readMetadata(byte[] bytes) {
    }

    /**
     * 
     * {@inheritDoc}
     */
    @Override
    public boolean merge() {
        return false;
    }

}
