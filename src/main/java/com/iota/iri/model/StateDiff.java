package com.iota.iri.model;

import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Serializer;
import org.apache.commons.lang3.ArrayUtils;

import javax.naming.OperationNotSupportedException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Creates a persistable State object, used to map addresses with values in the DB and snapshots.
 */
public class StateDiff implements Persistable {

    /** The map storing the address and balance of the current object */
    public Map<Hash, Long> state;

    /**
     * Returns a byte array of the state map contained in the object. If no data is present in the state,
     * a new empty byte array is returned instead.
     */
    @Override
    public byte[] bytes(){
        int size = state.size();
        if (size == 0) {
            return new byte[0];
        }

        byte[] temp = new byte[size * (Hash.SIZE_IN_BYTES + Long.BYTES)];
        int index = 0;
        for (Entry<Hash,Long> entry : state.entrySet()){
            byte[] key = entry.getKey().bytes();
            System.arraycopy(key, 0, temp, index, key.length);
            index += key.length;

            byte[] value = Serializer.serialize(entry.getValue());
            System.arraycopy(value, 0, temp, index, value.length);
            index += value.length;
        }
        return temp;
    }

    /**
     * Iterates through the given byte array and populates the state map with the contained address
     * hashes and the associated balance.
     *
     * @param bytes The source data to be placed in the State
     */
    public void read(byte[] bytes) {
        int i;
        state = new HashMap<>();
        if(bytes != null) {
            for (i = 0; i < bytes.length; i += Hash.SIZE_IN_BYTES + Long.BYTES) {
                state.put(HashFactory.ADDRESS.create(bytes, i, Hash.SIZE_IN_BYTES),
                        Serializer.getLong(Arrays.copyOfRange(bytes, i + Hash.SIZE_IN_BYTES, i + Hash.SIZE_IN_BYTES + Long.BYTES)));
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
    public boolean canMerge() {
        return false;
    }

    @Override
    public Persistable mergeInto(Persistable source) throws OperationNotSupportedException {
            throw new OperationNotSupportedException("This object is not mergeable");
    }

    @Override
    public boolean exists() {
        return !(this.state == null || this.state.isEmpty());
    }

}
