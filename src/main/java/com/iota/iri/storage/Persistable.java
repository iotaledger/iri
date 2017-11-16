package com.iota.iri.storage;

import javax.naming.OperationNotSupportedException;
import java.io.Serializable;
import java.util.List;

/**
 * Created by paul on 5/6/17.
 */
public interface Persistable extends Serializable {
    byte[] bytes();
    void read(byte[] bytes);
    byte[] metadata();
    void readMetadata(byte[] bytes);
    boolean merge();

    /**
     * @return true if the {@link Persistable} object can be splitBytes to multiple byte arrays.
     */
    boolean isSplittable();

    /**
     * @return a list of byte arrays that represent the serialized object.
     * @throws OperationNotSupportedException if {@link #isSplittable()} returns false.
     * @param maxByteLength
     */
    List<byte[]> splitBytes(int maxByteLength) throws OperationNotSupportedException;
}
