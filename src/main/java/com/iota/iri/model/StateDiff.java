package com.iota.iri.model;

import com.iota.iri.storage.Persistable;
import com.iota.iri.utils.Serializer;
import org.apache.commons.lang3.ArrayUtils;

import javax.naming.OperationNotSupportedException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by paul on 5/6/17.
 */
public class StateDiff implements Persistable {
    public Map<Hash, Long> state;

    public byte[] bytes() {
        return state.entrySet().parallelStream()
                .map(entry -> ArrayUtils.addAll(entry.getKey().bytes(), Serializer.serialize(entry.getValue())))
                .reduce(ArrayUtils::addAll)
                .orElse(new byte[0]);
    }
    public void read(byte[] bytes) {
        int i;
        state = new HashMap<>();
        if(bytes != null) {
            for (i = 0; i < bytes.length; i += Hash.SIZE_IN_BYTES + Long.BYTES) {
                state.put(new Hash(bytes, i, Hash.SIZE_IN_BYTES),
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
