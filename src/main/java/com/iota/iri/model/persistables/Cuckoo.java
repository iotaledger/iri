package com.iota.iri.model.persistables;

import java.util.BitSet;

import org.apache.commons.lang3.ArrayUtils;

import com.iota.iri.model.IntegerIndex;
import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Serializer;

/**
 * Persistable to manage the data we get as a result of pruning a milestone and its transactions
 */
public class Cuckoo implements Persistable {

    /**
     * The filter number it belonged to in previous cycles
     */
    public IntegerIndex filterId;
    
    /**
     * 
     */
    public BitSet filterBits;
    
    /**
     * 
     * {@inheritDoc}
     */
    @Override
    public byte[] bytes() {
        byte[] num = filterId.bytes();
        return ArrayUtils.addAll(num, filterBits.toByteArray());
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
            filterId = new IntegerIndex(Serializer.getInteger(bytes, 0));
            
            short start = 4;
            filterBits = new BitSet(bytes.length - start);
            for (int i = start; i < bytes.length; i++) {
                filterBits.set(i-start, bytes[i]);
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
